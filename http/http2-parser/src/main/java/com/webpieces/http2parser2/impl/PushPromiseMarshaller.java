package com.webpieces.http2parser2.impl;

import java.nio.ByteBuffer;
import java.util.List;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;

import com.webpieces.http2parser.api.dto.PushPromiseFrame;
import com.webpieces.http2parser.api.dto.lib.AbstractHttp2Frame;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;
import com.webpieces.http2parser.impl.DataSplit;
import com.webpieces.http2parser.impl.PaddingUtil;

public class PushPromiseMarshaller extends AbstractFrameMarshaller implements FrameMarshaller {
    PushPromiseMarshaller(BufferPool bufferPool, DataWrapperGenerator dataGen) {
        super(bufferPool, dataGen);
    }

	@Override
	public DataWrapper marshal(Http2Frame frame) {
        PushPromiseFrame castFrame = (PushPromiseFrame) frame;
        int paddingSize = castFrame.getPadding().getReadableSize();

        byte value = 0x0;
        if (castFrame.isEndHeaders()) value |= 0x4;
        if (paddingSize > 0) value |= 0x8;

        ByteBuffer prelude = bufferPool.nextBuffer(4);
        prelude.putInt(castFrame.getPromisedStreamId());
        prelude.flip();

        DataWrapper headersDW = castFrame.getHeaderFragment();
        DataWrapper finalDW = dataGen.chainDataWrappers(
                dataGen.wrapByteBuffer(prelude),
                headersDW);
        
        DataWrapper payload = PaddingUtil.padDataIfNeeded(finalDW, castFrame.getPadding());
        
		return super.marshalFrame(frame, value, payload);
	}

	@Override
	public AbstractHttp2Frame unmarshal(Http2MementoImpl state, DataWrapper framePayloadData) {
        PushPromiseFrame frame = new PushPromiseFrame();
		super.unmarshalFrame(state, frame);

		byte flags = state.getFrameHeaderData().getFlagsByte();
        frame.setEndHeaders((flags & 0x4) == 0x4);
        boolean isPadded = (flags & 0x8) == 0x8;

        List<? extends DataWrapper> split = dataGen.split(framePayloadData, 4);
        ByteBuffer prelude = bufferPool.createWithDataWrapper(split.get(0));

        
        DataSplit padSplit = PaddingUtil.extractPayloadAndPadding(isPadded, split.get(1), frame.getStreamId());
        frame.setHeaderFragment(padSplit.getPayload());
        frame.setPadding(padSplit.getPadding());
        frame.setPromisedStreamId(prelude.getInt());
        bufferPool.releaseBuffer(prelude);
            
		return frame;
	}

}
