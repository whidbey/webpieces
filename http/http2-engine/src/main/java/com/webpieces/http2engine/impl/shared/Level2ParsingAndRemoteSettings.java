package com.webpieces.http2engine.impl.shared;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.util.acking.AckAggregator;
import org.webpieces.util.acking.ByteAckTracker;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.hpack.api.HpackParser;
import com.webpieces.hpack.api.UnmarshalState;
import com.webpieces.hpack.api.dto.Http2Trailers;
import com.webpieces.http2engine.api.ConnectionClosedException;
import com.webpieces.http2engine.api.client.Http2Config;
import com.webpieces.http2engine.api.error.ConnectionFailure;
import com.webpieces.http2engine.api.error.ReceivedGoAway;
import com.webpieces.http2engine.impl.shared.data.HeaderSettings;
import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.GoAwayFrame;
import com.webpieces.http2parser.api.dto.PingFrame;
import com.webpieces.http2parser.api.dto.PriorityFrame;
import com.webpieces.http2parser.api.dto.RstStreamFrame;
import com.webpieces.http2parser.api.dto.SettingsFrame;
import com.webpieces.http2parser.api.dto.UnknownFrame;
import com.webpieces.http2parser.api.dto.WindowUpdateFrame;
import com.webpieces.http2parser.api.dto.error.CancelReasonCode;
import com.webpieces.http2parser.api.dto.error.ConnectionException;
import com.webpieces.http2parser.api.dto.error.StreamException;
import com.webpieces.http2parser.api.dto.lib.Http2Msg;

public abstract class Level2ParsingAndRemoteSettings {

	private static final Logger log = LoggerFactory.getLogger(Level2ParsingAndRemoteSettings.class);

	private HpackParser lowLevelParser;
	private UnmarshalState parsingState;
	private Level7MarshalAndPing marshalLayer;
	private String logId;
	protected Level3IncomingSynchro syncro;
	private HeaderSettings localSettings;
	private ByteAckTracker tracker2 = new ByteAckTracker();

	protected Level3OutgoingSynchro outSyncro;

	public Level2ParsingAndRemoteSettings(
			String logId,
			Level3IncomingSynchro syncro,
			Level3OutgoingSynchro outSyncro,
			Level7MarshalAndPing notifyListener, 
			HpackParser lowLevelParser, 
			Http2Config config
	) {
		this.outSyncro = outSyncro;
		this.localSettings = config.getLocalSettings();
		this.logId = logId;
		
		this.syncro = syncro;
		this.marshalLayer = notifyListener;
		this.lowLevelParser = lowLevelParser;
		parsingState = lowLevelParser.prepareToUnmarshal(logId, 4096, localSettings.getHeaderTableSize(), localSettings.getMaxFrameSize());
	}

	/**
	 * NOT thread safe!!! BUT channelmanager keeps everything virtually thread-safe (ie. it's all going to come in order)
	 * @return 
	 * @return 
	 */
	public CompletableFuture<Void> parse(DataWrapper newData) {
		CompletableFuture<Void> future;
		try {
			future = parseImpl(newData);
		} catch(Throwable t) {
			future = new CompletableFuture<Void>();
			future.completeExceptionally(t);
		}
		
		return future.handle((resp, t) -> handleFinalError(resp, t));
	}
	private Void handleFinalError(Object object, Throwable e) {
		if(e == null)
			return null;
		else if(e instanceof ConnectionException) {
			log.error("shutting the connection down due to error", e);
			ConnectionFailure reset = new ConnectionFailure((ConnectionException)e);
			syncro.sendGoAwayToSvrAndResetAllToApp(reset).exceptionally( t -> logExc("connection", t)); //send GoAway
		} else {
			log.error("shutting the connection down due to error(MAKE sure your clients try..catch, exceptions)", e);
			ConnectionException exc = new ConnectionException(CancelReasonCode.BUG, logId, 0, e.getMessage(), e);
			ConnectionFailure reset = new ConnectionFailure((ConnectionException)exc);
			syncro.sendGoAwayToSvrAndResetAllToApp(reset).exceptionally( t -> logExc("connection", t)); //send GoAwa
		}
		return null;
	}
	
	private Void handleError(Object object, Throwable e) {
		if(e == null) 
			return null;
		else if(e instanceof ConnectionClosedException) {
			log.error("Normal exception since we are closing and they do not know yet", e);
		} else if(e instanceof StreamException) {
			log.error("shutting the stream down due to error", e);
			syncro.sendRstToServerAndApp((StreamException) e).exceptionally( t -> logExc("stream", t));
		} else 
			handleFinalError(object, e);
		
		return null;
	}

	private Void logExc(String thing, Throwable t) {
		log.error("error trying to close "+thing, t);
		return null;
	}
	
	public CompletableFuture<Void> parseImpl(DataWrapper newData) {
		
		CompletableFuture<Void> future2 = tracker2.addBytesToTrack(newData.getReadableSize());
		
		parsingState = lowLevelParser.unmarshal(parsingState, newData);
		
		List<Http2Msg> parsedMessages = parsingState.getParsedFrames();
		
		int numBytesParsed = parsingState.getNumBytesJustParsed();
		
		AckAggregator aggregator = new AckAggregator(parsedMessages.size(), numBytesParsed, tracker2);
		
		CompletableFuture<Void> future = CompletableFuture.completedFuture((Void)null);
		for(Http2Msg lowLevelFrame : parsedMessages) {
			future = future.thenCompose(v -> {
				return process(lowLevelFrame)
						.handle((s, t) -> aggregator.ack(s, t));
			});
		}
		return future2;
	}

	public CompletableFuture<Void> process(Http2Msg msg) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		try {
			future = processImpl(msg);
		} catch(Throwable e) {
			future.completeExceptionally(e);
		}
		
		return future.handle((v, t) -> handleError(v, t));
	}
	
	public CompletableFuture<Void> processImpl(Http2Msg msg) {
		if(log.isDebugEnabled())
			log.debug(() -> logId+"frame from socket="+msg);
		
		if(msg instanceof DataFrame) {
			return syncro.sendDataToApp((DataFrame) msg);
		} else if(msg instanceof Http2Trailers) {
			return syncro.sendTrailersToApp((Http2Trailers)msg);
		} else if(msg instanceof PriorityFrame) {
			return syncro.sendPriorityFrameToApp((PriorityFrame) msg);
		} else if(msg instanceof RstStreamFrame) {
			return syncro.sendRstToApp((RstStreamFrame) msg);
		} else if(msg instanceof UnknownFrame) {
			return syncro.sendUnkownFrameToApp((UnknownFrame)msg);
		} else if(msg instanceof GoAwayFrame) {
			ReceivedGoAway goAway = new ReceivedGoAway(logId+" Far end sent goaway to us", (GoAwayFrame)msg);
			return syncro.sendGoAwayToApp(goAway).exceptionally( t -> logExc("connection", t)); //send GoAwa
		} else if(msg instanceof PingFrame) {
			return marshalLayer.processPing((PingFrame)msg);
		} else if(msg instanceof SettingsFrame) {
			return processHttp2SettingsFrame((SettingsFrame) msg);
		} else if(msg instanceof WindowUpdateFrame){
			return syncro.updateWindowSize((WindowUpdateFrame)msg);
		} 
		
		return processSpecific(msg);
	}

	protected abstract CompletableFuture<Void> processSpecific(Http2Msg msg);

	private CompletableFuture<Void> processHttp2SettingsFrame(SettingsFrame settings) {
		if(settings.isAck()) {
			log.info("server acked our settings frame");
			return CompletableFuture.completedFuture(null);
		} else {
			log.info("applying remote settings frame");
			
			return syncro.applyRemoteSettingsAndAck(settings);
		}
	}

}
