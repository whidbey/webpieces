package org.webpieces.httpparser.impl;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.httpparser.api.HttpParser;
import org.webpieces.httpparser.api.HttpParserFactory;
import org.webpieces.httpparser.api.MarshalState;
import org.webpieces.httpparser.api.Memento;
import org.webpieces.httpparser.api.ParseException;
import org.webpieces.httpparser.api.common.Header;
import org.webpieces.httpparser.api.common.KnownHeaderName;
import org.webpieces.httpparser.api.dto.Http2MarkerMessage;
import org.webpieces.httpparser.api.dto.HttpChunk;
import org.webpieces.httpparser.api.dto.HttpChunkExtension;
import org.webpieces.httpparser.api.dto.HttpData;
import org.webpieces.httpparser.api.dto.HttpLastChunk;
import org.webpieces.httpparser.api.dto.HttpMessage;
import org.webpieces.httpparser.api.dto.HttpMessageType;
import org.webpieces.httpparser.api.dto.HttpPayload;
import org.webpieces.httpparser.api.dto.HttpRequest;
import org.webpieces.httpparser.api.dto.HttpRequestLine;
import org.webpieces.httpparser.api.dto.HttpRequestMethod;
import org.webpieces.httpparser.api.dto.HttpResponse;
import org.webpieces.httpparser.api.dto.HttpResponseStatus;
import org.webpieces.httpparser.api.dto.HttpResponseStatusLine;
import org.webpieces.httpparser.api.dto.HttpUri;
import org.webpieces.httpparser.api.dto.HttpVersion;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

public class HttpParserImpl implements HttpParser {

	private static final Logger log = LoggerFactory.getLogger(HttpParserImpl.class);
	private static final Charset iso8859_1 = HttpParserFactory.iso8859_1;
	private static final String TRAILER_STR = "\r\n";
	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	
	private ConvertAscii conversion = new ConvertAscii();
	private BufferPool pool;

	
	public HttpParserImpl(BufferPool pool) {
		this.pool = pool;
	}
	
	@Override
	public MarshalState prepareToMarshal() {
		return new MarshalStateImpl();
	}
	
	@Override
	public ByteBuffer marshalToByteBuffer(MarshalState state, HttpPayload request) {
		//modify later to go from String straight to ByteBuffer instead...
		byte[] data = marshalToBytes(state, request);
		ByteBuffer buffer = ByteBuffer.wrap(data);
		return buffer;
	}
	
	public byte[] marshalToBytes(MarshalState s, HttpPayload payload) {
		MarshalStateImpl state = (MarshalStateImpl) s;
		if(state.getParsingDataSize() != null) {
			return parseData(state, payload);
		} else if(payload.getMessageType() == HttpMessageType.CHUNK || payload.getMessageType() == HttpMessageType.LAST_CHUNK) {
			return chunkedBytes((HttpChunk)payload);
		}
		
		HttpMessage msg = (HttpMessage) payload;
		String result = marshalHeaders(payload);
		
		Header header = msg.getHeaderLookupStruct().getHeader(KnownHeaderName.CONTENT_LENGTH);
		if(header != null && !header.getValue().equals("0")) {
			String value = header.getValue();
			int lengthOfBodyFromHeader = toInteger(value, ""+header);			
			state.setParsingDataSize(lengthOfBodyFromHeader);
		}
		
		byte[] stringPiece = result.getBytes(iso8859_1);
		
		return stringPiece;
	}

	private byte[] parseData(MarshalStateImpl state, HttpPayload payload) {
		if(!(payload instanceof HttpData))
			throw new IllegalStateException("You passed in a request or response earlier with Content-Length so must pass in all the HttpData next not this="+payload);

		HttpData data = payload.getHttpData();
		DataWrapper body = data.getBodyNonNull();
		state.addMoreBytes(body.getReadableSize());
		
		if(state.getTotalRead() > state.getParsingDataSize())
			throw new IllegalStateException("Content-Length was "+state.getParsingDataSize()+" but you have so far passed in "+state.getTotalRead()+" bytes");
		else if(state.getTotalRead() == state.getParsingDataSize()) {
			state.resetDataReading();
		}
		return body.createByteArray();
	}

	private void copyData(DataWrapper body, byte[] data, int offset) {
		for(int i = 0; i < body.getReadableSize(); i++) {
			//TODO: Think about using System.arrayCopy here(what is faster?)
			data[offset + i] = body.readByteAt(i);
		}
	}

	private byte[] chunkedBytes(HttpChunk request) {
		DataWrapper dataWrapper = request.getBody();
		int size = dataWrapper.getReadableSize();

		String metaLine = request.createMetaLine();
		String lastPart = request.createTrailer();
		
		byte[] hex = metaLine.getBytes(iso8859_1);
		byte[] endData = lastPart.getBytes(iso8859_1);
		
		byte[] data = new byte[hex.length+size+endData.length];

		//copy chunk header of <size>/r/n
		System.arraycopy(hex, 0, data, 0, hex.length);
		
		copyData(dataWrapper, data, hex.length);

		//copy closing /r/n (and headers if isLastChunk)
		System.arraycopy(endData, 0, data, data.length-endData.length, endData.length);
		
		return data;
	}

	private Integer toInteger(String value, String line) {
		try {
			return Integer.valueOf(value);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("HttpMessage contains illegal line(could not convert value to Integer)="+line);
		}
	}

	@Override
	public String marshalToString(HttpPayload httpMsg) {
		//TODO: We could check Content-Type header and if text type, we could marshall it still?
		if(httpMsg.getMessageType() == HttpMessageType.CHUNK
				|| httpMsg.getMessageType() == HttpMessageType.DATA)
			throw new IllegalArgumentException("Cannot marshal http message with a body to a string");
		
		return marshalHeaders(httpMsg);
	}

	private String marshalHeaders(HttpPayload httpMsg) {
		if(httpMsg.getMessageType() == HttpMessageType.REQUEST)
			validate(httpMsg.getHttpRequest());
		else if(httpMsg.getMessageType() == HttpMessageType.RESPONSE)
			validate(httpMsg.getHttpResponse());
		
		//TODO: perhaps optimize and use StringBuilder on the Header for loop
		//Java optimizes most to StringBuilder but for a for loop, it doesn't all the time...
		StringBuilder builder = new StringBuilder();
		builder.append(httpMsg + "");
		return builder.toString();
	}

	private void validate(HttpResponse response) {
		HttpResponseStatusLine statusLine = response.getStatusLine();
		if(statusLine == null) {
			throw new IllegalArgumentException("response.statusLine is not set(call response.setStatusLine");
		}
		HttpResponseStatus status = statusLine.getStatus();
		if(status == null) {
			throw new IllegalArgumentException("response.statusLine.status is not set(call response.getStatusLine().setStatus())");
		} else if(status.getCode() == null) {
			throw new IllegalArgumentException("response.statusLine.status.code is not set(call response.getStatusLine().getStatus().setCode())");
		} else if(status.getReason() == null) {
			throw new IllegalArgumentException("response.statusLine.status.reason is not set");
		} else if(statusLine.getVersion() == null) {
			throw new IllegalArgumentException("response.statusLine.version is not set");
		}
	}

	private void validate(HttpRequest request) {
		HttpRequestLine requestLine = request.getRequestLine();
		if(requestLine == null) {
			throw new IllegalArgumentException("request.requestLine is not set(call request.setRequestLine()");
		} else if(requestLine.getMethod() == null) {
			throw new IllegalArgumentException("request.requestLine.method is not set(call request.getRequestLine().setMethod()");
		} else if(requestLine.getVersion() == null) {
			throw new IllegalArgumentException("request.requestLine.version is not set(call request.getRequestLine().setVersion()");
		}
	}

	@Override
	public Memento prepareToParse() {
		MementoImpl memento = new MementoImpl();
		memento.setLeftOverData(dataGen.emptyWrapper());
		return memento;
	}
	
	@Override
	public Memento parse(Memento state, DataWrapper moreData) {
		if(!(state instanceof MementoImpl)) {
			throw new IllegalArgumentException("You must always pass in the "
					+ "memento created in prepareToParse which we always hand back"
					+ "to you from this method.  It contains state of leftover data");
		}

		MementoImpl memento = (MementoImpl) state;
		int totalData = state.getLeftOverData().getReadableSize()+moreData.getReadableSize();
		memento = parse(memento, moreData);
		int bytesParsed = totalData - memento.getLeftOverData().getReadableSize();
		
		memento.setNumBytesJustParsed(bytesParsed);
		
		return memento;
	}
	
	private MementoImpl parse(MementoImpl memento, DataWrapper moreData) {
		log.trace(()->"Trying to parse message");
//		if(log.isDebugEnabled()) {
//			byte[] someData = moreData.createByteArray();
//			String readable = conversion.convertToReadableForm(someData);
//			log.info("about to parse=\n\n'"+readable+"'\n\n");
//		}

		//initialize state to need more data
		memento.setParsedMessages(new ArrayList<>());
		
		DataWrapper leftOverData = memento.getLeftOverData();
		DataWrapper	allData = dataGen.chainDataWrappers(leftOverData, moreData);
		memento.setLeftOverData(allData);
		
		if(memento.isInChunkParsingMode()) {
			processChunks(memento);
		} else if(memento.getContentLengthLeftToRead() != null) {
			readInContentLengthBody(memento);
		} else if(memento.getHalfParsedChunk() != null) {
			readInChunkBody(memento, false);
		}

		if(memento.getHalfParsedChunk() != null || memento.getContentLengthLeftToRead() != null)
			return memento;  //we are still reading in the body of a chunk
		
		//This is a bit tricky but memento.getReadingHttpMessagePoint will cause this method to 
		//return immediately if we are in the middle of processChunks or readInBody
		//BUT this is here because AFTER processChunks or readInBody is complete, it should process the next
		//response as well!!!
		findCrLnCrLnAndParseMessage(memento);
		return memento;
	}

	private void readInContentLengthBody(MementoImpl memento) {
		Integer toRead = memento.getContentLengthLeftToRead();
		
		DataWrapper data = memento.getLeftOverData();
		int readSize = Math.min(toRead, data.getReadableSize());
		
		if(readSize == 0) {
			//wait for more data
			return;
		} 
		
		int newSizeLeft = toRead - readSize;
		boolean isEos;
		if(newSizeLeft == 0) {
			isEos = true;
			memento.setContentLengthLeftToRead(null);
		} else {
			isEos = false;
			memento.setContentLengthLeftToRead(newSizeLeft);
		}
		
		List<? extends DataWrapper> split = dataGen.split(data, readSize);
		HttpData httpData = new HttpData(split.get(0), isEos);
		
		memento.setLeftOverData(split.get(1));
		
		memento.addMessage(httpData);
	}

	private void findCrLnCrLnAndParseMessage(MementoImpl memento) {
//		DataWrapper leftOverData2 = memento.getLeftOverData();
//		String msg = leftOverData2.createStringFrom(0, leftOverData2.getReadableSize(), Charset.defaultCharset());
//		System.out.println("msg="+msg);
		
		//We are looking for the \r\n\r\n  (or \n\n from bad systems) to
		//discover entire payload
		int i = memento.getReadingHttpMessagePointer();
		for(; i < memento.getLeftOverData().getReadableSize() - 3; i++) {
			boolean parsedAndSplitBuffer = processUntilRead(memento, i);
			if(parsedAndSplitBuffer) {
				//reset the index for reading
				i = 0;
			}
			
			
			//do not continue if we are reading the body...
			if(memento.getHalfParsedChunk() != null || memento.getContentLengthLeftToRead() != null) {
				break;
			} else if(memento.isHasHttpMarkerMsg())
				break;
		}
		memento.setReadingHttpMessagePointer(i);
	}

	/**
	 * Returns true if we split the buffers up or else false
	 */
	private boolean processUntilRead(MementoImpl memento, int i) {
		DataWrapper dataToRead = memento.getLeftOverData();
		byte firstByte = dataToRead.readByteAt(i);
		byte secondByte = dataToRead.readByteAt(i+1);
		byte thirdByte = dataToRead.readByteAt(i+2);
		byte fourthByte = dataToRead.readByteAt(i+3);
		
		//For debugging to see the 4 bytes that we are processing easier
//		log.error("This should be commented out, don't forget or we log this error");
//		byte[] data = dataToRead.createByteArray();
//		String fourBytesAre = conversion.convertToReadableForm(data, i, 4);
		
		boolean isFirstCr = conversion.isCarriageReturn(firstByte);
		boolean isSecondLineFeed = conversion.isLineFeed(secondByte);
		boolean isThirdCr = conversion.isCarriageReturn(thirdByte);
		boolean isFourthLineField = conversion.isLineFeed(fourthByte);
		
		if(isFirstCr && isSecondLineFeed && isThirdCr && isFourthLineField) {
			//Found end of http headers...
			processHttpMessageAndMaybeBody(memento, dataToRead, i);
			memento.setReadingHttpMessagePointer(0);
			return true;
		}
		
		//mark any positions for \r\n
		if(isFirstCr && isSecondLineFeed) {
			memento.addDemarcation(i);
		}		
		return false;
	}

	private void processHttpMessageAndMaybeBody(MementoImpl memento, DataWrapper dataToRead, int i) {		
		List<Integer> markedPositions = memento.getLeftOverMarkedPositions();
		memento.setLeftOverMarkedPositions(new ArrayList<Integer>());
		List<? extends DataWrapper> tuple = dataGen.split(dataToRead, i+4);
		DataWrapper toBeParsed = tuple.get(0);
		memento.setLeftOverData(tuple.get(1));
		memento.setReadingHttpMessagePointer(0);
		HttpMessage message = parseHttpMessage(memento, toBeParsed, markedPositions);
		if(memento.isHttp2()) {
			//shortcut to cut out so http2 parser can begin
			//memento.addMessage(message);
			return;
		}

		Header contentLenHeader = message.getHeaderLookupStruct().getHeader(KnownHeaderName.CONTENT_LENGTH);
		Header transferHeader = message.getHeaderLookupStruct().getLastInstanceOfHeader(KnownHeaderName.TRANSFER_ENCODING);

		if(transferHeader != null && "chunked".equals(transferHeader.getValue())) {
			memento.setInChunkParsingMode(true);
			processChunks(memento);
			return;
		} else if(contentLenHeader != null && !"0".equals(contentLenHeader.getValue())) {
			String value = contentLenHeader.getValue();
			int length = toInteger(value, ""+contentLenHeader);
			memento.setContentLengthLeftToRead(length);
			readInContentLengthBody(memento);
			return;
		}		
	}

	private void processChunks(MementoImpl memento) {
		if(memento.getHalfParsedChunk() != null) {
			readInChunkBody(memento, true);
			if(memento.getHalfParsedChunk() != null)
				return; //we are still reading in the body
		}
		
		
		int i = memento.getReadingHttpMessagePointer();
		for(;i < memento.getLeftOverData().getReadableSize() - 1; i++) 
		{
			DataWrapper dataToRead = memento.getLeftOverData();
			byte firstByte = dataToRead.readByteAt(i);
			byte secondByte = dataToRead.readByteAt(i+1);

			boolean isFirstCr = conversion.isCarriageReturn(firstByte);
			boolean isSecondLineFeed = conversion.isLineFeed(secondByte);
			
			if(isFirstCr && isSecondLineFeed) {
				readChunk(memento, i);
				//since we swapped out memento.getLeftOverData to be 
				//what's left, we can read from 0 again
				i = 0;
				if(!memento.isInChunkParsingMode() //we are done processing chunks
					|| memento.getHalfParsedChunk() != null) //we are in the middle of processing chunk body 	
					break; 
			}
		}
		memento.setReadingHttpMessagePointer(i);
	}

	private void readChunk(MementoImpl memento, int i) {
		HttpChunk chunk = createHttpChunk(memento, i);
		memento.setHalfParsedChunk(chunk);
		readInChunkBody(memento, true);
		
		if(chunk.getBody() != null && chunk.getBody().getReadableSize() == 0) {
			//this is the last chunk as it is 0 size
			memento.setInChunkParsingMode(false);
		}
	}

	private HttpChunk createHttpChunk(MementoImpl memento, int i) {
		DataWrapper dataToRead = memento.getLeftOverData();
		//split off the header AND /r/n (ie. the +2)
		List<? extends DataWrapper> split = dataGen.split(dataToRead, i+2);
		DataWrapper chunkMetaData = split.get(0);
		memento.setLeftOverData(split.get(1));
		
		List<HttpChunkExtension> extensions = new ArrayList<>();
		
		String chunkMetaStr = chunkMetaData.createStringFrom(0, chunkMetaData.getReadableSize(), iso8859_1);
		String hexSize = chunkMetaStr.trim();
		if(chunkMetaStr.contains(";")) {
			String[] extensionsArray = chunkMetaStr.split(";");
			hexSize = extensionsArray[0];
			for(int n = 1; n < extensionsArray.length; n++) {
				HttpChunkExtension ext = createExtension(extensionsArray[n]);
				extensions.add(ext);
			}
		}

		int chunkSize = Integer.parseInt(hexSize, 16);
		HttpChunk chunk = new HttpChunk();
		if(chunkSize == 0)
			chunk = new HttpLastChunk();
		
		//must read in all the data of the chunk AND /r/n
		int size = 2 + chunkSize;
		memento.setNumBytesLeftToReadOnChunk(size);
		
		return chunk;
	}

	private HttpChunkExtension createExtension(String extension) {
		if(!extension.contains("=")) {
			return new HttpChunkExtension(extension);
		}
		
		//if there are multiple '=' in the extensions, we can only split on first one
		int indexOf = extension.indexOf('=');
		String name = extension.substring(0, indexOf);
		String value = extension.substring(indexOf);
		return new HttpChunkExtension(name, value);
	}

	//Returns true if body read in and false otherwise
	private void readInChunkBody(MementoImpl memento, boolean stripAndCompareLastTwo) {
		HttpChunk message = memento.getHalfParsedChunk();
		DataWrapper dataToRead = memento.getLeftOverData();
		int readableSize = dataToRead.getReadableSize();
		int numBytesNeeded = memento.getNumBytesLeftToReadOnChunk();
		
		if(numBytesNeeded <= readableSize) {
			List<? extends DataWrapper> split = dataGen.split(dataToRead, numBytesNeeded);
			DataWrapper data = split.get(0);
			if(stripAndCompareLastTwo) {
				List<? extends DataWrapper> splitPieces = dataGen.split(data, data.getReadableSize()-2);
				data = splitPieces.get(0);
				DataWrapper trailer = splitPieces.get(1);
				String trailerStr = trailer.createStringFrom(0, trailer.getReadableSize(), iso8859_1);
				if(!TRAILER_STR.equals(trailerStr))
					throw new IllegalStateException("The chunk did not end with \\r\\n .  The format is invalid");
			}
			
			message.setBody(data);
			memento.setLeftOverData(split.get(1));
			memento.setNumBytesLeftToReadOnChunk(0);
			memento.addMessage(message);
			
			//clear any cached message we were waiting for more data for
			memento.setHalfParsedChunk(null);
			return;
		}
	}

	private HttpMessage parseHttpMessage(MementoImpl memento, DataWrapper toBeParsed, List<Integer> markedPositions) {
		List<String> lines = new ArrayList<>();
		
		//Add the last line..
		markedPositions.add(toBeParsed.getReadableSize());
		int offset = 0;
		for(Integer mark : markedPositions) {
			int len = mark - offset;
			String line = toBeParsed.createStringFrom(offset, len, iso8859_1);
			lines.add(line.trim());
			offset = mark;
		}
		markedPositions.clear();

		//buffer processed...release to be re-used now..
		toBeParsed.releaseUnderlyingBuffers(pool);
		
		String firstLine = lines.get(0).trim();
		
		if(memento.isHttp2()) {
			return checkSecondCase(memento, lines);
		} else if(firstLine.startsWith("HTTP/")) {
			return parseResponse(memento, lines);
		} else if(lines.size() == 1 && memento.getParsedMessages().size() == 0) {
			//special case where there is no headers AND it could be http 2 preface
			return checkSpecialCase(memento, lines);
		} else {
			return parseRequest(memento, lines);
		}
	}

	private HttpMessage checkSecondCase(MementoImpl memento, List<String> lines) {
		String requestLine = lines.get(0);
		if("SM".equals(requestLine)) {
			Http2MarkerMessage msg = new Http2MarkerMessage();
			//we are http2 so return an Http2Message and SHORT-CIRCUIT FURTHER PARSING
			memento.setHasHttpMarkerMsg(true);
			memento.addMessage(msg);
			return msg;
		}

		throw new IllegalArgumentException("PRI * HTTP/2.0\\r\\n received but then missing SM="+requestLine);
	}

	private HttpMessage checkSpecialCase(MementoImpl memento, List<String> lines) {
		String requestLine = lines.get(0);
		if("PRI * HTTP/2.0".equals(requestLine)) {
			//we are http2 so return an Http2Message and SHORT-CIRCUIT FURTHER PARSING
			memento.setHttp2(true);
			return null;
		}
		
		return parseRequest(memento, lines);
	}

	private HttpMessage parseRequest(MementoImpl memento, List<String> lines) {
		//remove first line...
		String firstLine = lines.remove(0);
		String[] firstLinePieces = firstLine.split("\\s+");
		if(firstLinePieces.length != 3) {
			throw new ParseException("Unable to parse invalid http request due to first line being invalid=" + firstLine+" all Lines="+lines);
		}
		
		HttpRequestMethod method = new HttpRequestMethod(firstLinePieces[0]);
		HttpUri uri = new HttpUri(firstLinePieces[1]);
		
		HttpVersion version = parseVersion(firstLinePieces[2], firstLine);
		
		HttpRequestLine httpRequestLine = new HttpRequestLine();
		httpRequestLine.setMethod(method);
		httpRequestLine.setUri(uri);
		httpRequestLine.setVersion(version);
		
		HttpRequest request = new HttpRequest();
		request.setRequestLine(httpRequestLine);
		
		parseHeaders(lines, request);
		
		memento.addMessage(request);
		return request;
	}

	private HttpVersion parseVersion(String versionString, String firstLine) {
		if(!versionString.startsWith("HTTP/")) {
			throw new ParseException("Invalid version in http request first line not prefixed with HTTP/.  line="+firstLine);
		}
		
		String ver = versionString.substring(5, versionString.length());
		HttpVersion version = new HttpVersion();
		version.setVersion(ver);
		return version;
	}

	private void parseHeaders(List<String> lines, HttpMessage httpMessage) {
		//TODO: one header can be multiline and we need to fix this code for that
		//ie. the spec says you can split a head in multiple lines(ick!!!)
		for(String line : lines) {
			Header header = parseHeader(line);
			httpMessage.addHeader(header);
		}
	}

	private Header parseHeader(String line) {
		//can't use split in case there are two ':' ...one in the value and one as the delimeter
		int indexOf = line.indexOf(":");
		if(indexOf < 0)
			throw new IllegalArgumentException("bad header line="+ line);
		String value = line.substring(indexOf+1).trim();
		String name = line.substring(0, indexOf);
		Header header = new Header();
		header.setName(name.trim());
		header.setValue(value.trim());
		return header;
	}

	private HttpMessage parseResponse(MementoImpl memento, List<String> lines) {
		//remove first line...
		String firstLine = lines.remove(0);
		//In the case of response, a reason may contain spaces so we must split on first and second
		//whitespace only
		int indexOf = firstLine.indexOf(" ");
		if(indexOf < 0)
			throw new IllegalArgumentException("The first line of http request is invalid="+ firstLine);
		String versionStr = firstLine.substring(0, indexOf).trim();
		String tail = firstLine.substring(indexOf).trim();
		
		int indexOf2 = tail.indexOf(" ");
		if(indexOf2 < 0)
			throw new IllegalArgumentException("The first line of http request is invalid="+ firstLine);
		String codeStr = tail.substring(0, indexOf2).trim();
		String reason = tail.substring(indexOf2).trim();
		
		HttpVersion version2 = parseVersion(versionStr, firstLine);

		HttpResponseStatus status = new HttpResponseStatus();
		Integer codeVal = toInteger(codeStr, firstLine);
		if(codeVal <= 0 || codeVal >= 1000)
			throw new IllegalArgumentException("invalid status code.  response line="+firstLine);
		status.setCode(codeVal);
		status.setReason(reason);
		
		HttpResponseStatusLine httpRequestLine = new HttpResponseStatusLine();
		httpRequestLine.setStatus(status);
		httpRequestLine.setVersion(version2);
		
		HttpResponse response = new HttpResponse();
		response.setStatusLine(httpRequestLine);

		parseHeaders(lines, response);

		memento.addMessage(response);
		return response;
	}

//	@Override
//	public HttpPayload unmarshal(byte[] msg) {
//		Memento memento = prepareToParse();
//		DataWrapper dataWrapper = dataGen.wrapByteArray(msg);
//		Memento parsedData = parse(memento, dataWrapper);
//		if(parsedData.getStatus() == ParsedStatus.MSG_PARSED_AND_LEFTOVER_DATA)
//			throw new IllegalArgumentException("There is more data than one http message.  Use unmarshalAsync instead");
//		else if(parsedData.getStatus() == ParsedStatus.NEED_MORE_DATA)
//			throw new IllegalArgumentException("This http message is not complete.  Use unmarshalAsynch instead or "
//					+ "fix client code to pass in complete http message(or report a bug if it is this libraries fault)");
//		
//		List<HttpPayload> messages = parsedData.getParsedMessages();
//		if(messages.size() != 1)
//			throw new IllegalArgumentException("You passed in data for more than one http messages.  number of http messages="+messages.size());
//		return messages.get(0);
//	}
}
