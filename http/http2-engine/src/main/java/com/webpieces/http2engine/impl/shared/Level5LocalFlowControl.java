package com.webpieces.http2engine.impl.shared;

import java.util.concurrent.CompletableFuture;

import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.http2parser.api.ConnectionException;
import com.webpieces.http2parser.api.ParseFailReason;
import com.webpieces.http2parser.api.StreamException;
import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.WindowUpdateFrame;
import com.webpieces.http2parser.api.dto.lib.PartialStream;

public class Level5LocalFlowControl {

	private static final Logger log = LoggerFactory.getLogger(Level5LocalFlowControl.class);
	private Level6MarshalAndPing marshalLayer;
	private long connectionLocalWindowSize;
	private long totalSent = 0;
	private long totalRecovered = 0;
	private EngineResultListener notifyListener;

	public Level5LocalFlowControl(
			Level6MarshalAndPing marshalLayer,
			EngineResultListener notifyListener,
			HeaderSettings localSettings
	) {
		this.marshalLayer = marshalLayer;
		this.notifyListener = notifyListener;
		this.connectionLocalWindowSize = localSettings.getInitialWindowSize();
	}

	public CompletableFuture<Void> fireToClient(Stream stream, PartialStream payload) {
		if(!(payload instanceof DataFrame)) {
			return notifyListener.sendPieceToClient(stream, payload);
		}
		
		DataFrame f = (DataFrame) payload;
		long frameLength = f.getTransmitFrameLength();

		if(frameLength > connectionLocalWindowSize) {
			throw new ConnectionException(ParseFailReason.FLOW_CONTROL_ERROR, f.getStreamId(), 
					"connectionLocalWindowSize too small="+connectionLocalWindowSize
					+" frame len="+frameLength+" for frame="+f);
		} else if(frameLength > stream.getLocalWindowSize()) {
			throw new StreamException(ParseFailReason.FLOW_CONTROL_ERROR, f.getStreamId(), 
					"connectionLocalWindowSize too small="+connectionLocalWindowSize
					+" frame len="+frameLength+" for frame="+f);
		}
		
		totalSent += frameLength;
		connectionLocalWindowSize -= frameLength;
		stream.incrementLocalWindow(-frameLength);
		log.info("received framelen="+frameLength+" newConnectionWindowSize="
				+connectionLocalWindowSize+" streamSize="+stream.getLocalWindowSize()+" totalSent="+totalSent);
		
		return notifyListener.sendPieceToClient(stream, payload)
			.thenApply(c -> updateFlowControl(frameLength, stream));
	}

	private Void updateFlowControl(long frameLength, Stream stream) {
		if(frameLength == 0)
			return null; //nothing to do if it is a 0 length frame.  
		
		//TODO: we could optimize this to send very large window updates and send less window updates instead of
		//what we do currently sending many increase window by 13 byte updates and such.
		connectionLocalWindowSize += frameLength;
		stream.incrementLocalWindow(frameLength);
		totalRecovered += frameLength;

		int len = (int) frameLength;
		WindowUpdateFrame w1 = new WindowUpdateFrame();
		w1.setStreamId(0);
		w1.setWindowSizeIncrement(len);		

		marshalLayer.sendFrameToSocket(w1);
		
		if(!stream.isClosed()) {
			
			//IF the stream is not closed, update flow control
			WindowUpdateFrame w2 = new WindowUpdateFrame();
			w2.setStreamId(stream.getStreamId());
			w2.setWindowSizeIncrement(len);
			
			log.info("sending BOTH WUF increments. framelen="+frameLength+" recovered="+totalRecovered );
			marshalLayer.sendFrameToSocket(w2);
		} else {
			log.info("sending WUF increments. framelen="+frameLength+" recovered="+totalRecovered);
		}

		return null;
	}
	
}
