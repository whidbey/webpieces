package com.webpieces.http2parser2.impl;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;

import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.lib.AbstractHttp2Frame;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;
import com.webpieces.http2parser.impl.DataSplit;
import com.webpieces.http2parser.impl.PaddingUtil;

public class DataMarshaller extends AbstractFrameMarshaller implements FrameMarshaller  {

    DataMarshaller(BufferPool bufferPool, DataWrapperGenerator dataGen) {
        super(bufferPool, dataGen);
    }

	@Override
	public DataWrapper marshal(Http2Frame frame) {
        DataFrame castFrame = (DataFrame) frame;
        int paddingSize = castFrame.getPadding().getReadableSize();

        byte value = (byte) 0x0;
        if (castFrame.isEndStream()) value |= 0x1;
        if (paddingSize > 0) value |= 0x8;
        
        DataWrapper dataPayload = PaddingUtil.padDataIfNeeded(castFrame.getData(), castFrame.getPadding());
		return super.marshalFrame(frame, value, dataPayload);
	}

	@Override
	public AbstractHttp2Frame unmarshal(Http2MementoImpl state, DataWrapper framePayloadData) {
        DataFrame frame = new DataFrame();
        super.unmarshalFrame(state, frame);
        
        byte flags = state.getFrameHeaderData().getFlagsByte();
        frame.setEndStream((flags & 0x1) == 0x1);
        boolean isPadded = (flags & 0x8) == 0x8;

        DataSplit split = PaddingUtil.extractPayloadAndPadding(isPadded, framePayloadData, frame.getStreamId());
        frame.setData(split.getPayload());
        frame.setPadding(split.getPadding());
        
		return frame;
	}
}
