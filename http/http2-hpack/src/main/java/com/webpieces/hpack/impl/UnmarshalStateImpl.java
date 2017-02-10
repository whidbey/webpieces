package com.webpieces.hpack.impl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.twitter.hpack.Decoder;
import com.webpieces.hpack.api.UnmarshalState;
import com.webpieces.http2parser.api.Http2Memento;
import com.webpieces.http2parser.api.dto.lib.HasHeaderFragment;
import com.webpieces.http2parser.api.dto.lib.Http2Msg;

public class UnmarshalStateImpl implements UnmarshalState {

	private HeaderDecoding decoding;
	private Decoder decoder;
	private Http2Memento lowLevelState;
	
    private List<HasHeaderFragment> headersToCombine = new LinkedList<>();
	private List<Http2Msg> parsedFrames = new ArrayList<>();
	
	public UnmarshalStateImpl(Http2Memento lowLevelState, HeaderDecoding decoding, Decoder decoder) {
		this.lowLevelState = lowLevelState;
		this.decoding = decoding;
		this.decoder = decoder;
	}

	@Override
	public List<Http2Msg> getParsedFrames() {
		return parsedFrames;
	}

	@Override
	public int getLeftOverDataSize() {
		return lowLevelState.getLeftOverData().getReadableSize();
	}

	public Http2Memento getLowLevelState() {
		return lowLevelState;
	}

	public List<HasHeaderFragment> getHeadersToCombine() {
		return headersToCombine;
	}

	public Decoder getDecoder() {
		return decoder;
	}

	@Override
	public void setDecoderMaxTableSize(int newSize) {
		decoding.setMaxHeaderTableSize(decoder, newSize);
	}

	@Override
	public void setIncomingMaxFrameSize(long maxFrameSize) {
		lowLevelState.setIncomingMaxFrameSize(maxFrameSize);
	}

	public void clearParsedFrames() {
		parsedFrames = new ArrayList<>();
	}


}
