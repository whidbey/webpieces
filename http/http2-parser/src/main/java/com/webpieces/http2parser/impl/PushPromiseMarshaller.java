package com.webpieces.http2parser.impl;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;

import com.webpieces.http2parser.api.dto.PushPromiseFrame;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;

public class PushPromiseMarshaller extends FrameMarshallerImpl {
    PushPromiseMarshaller(BufferPool bufferPool, DataWrapperGenerator dataGen) {
        super(bufferPool, dataGen);
    }

    @Override
    public DataWrapper marshalPayload(Http2Frame frame) {
        PushPromiseFrame castFrame = (PushPromiseFrame) frame;

        ByteBuffer prelude = bufferPool.nextBuffer(4);
        prelude.putInt(castFrame.getPromisedStreamId());
        prelude.flip();

        DataWrapper headersDW = castFrame.getHeaderFragment();
        DataWrapper finalDW = dataGen.chainDataWrappers(
                dataGen.wrapByteBuffer(prelude),
                headersDW);
        return castFrame.getPadding().padDataIfNeeded(finalDW);
    }

    @Override
    public byte marshalFlags(Http2Frame frame) {
        PushPromiseFrame castFrame = (PushPromiseFrame) frame;

        byte value = 0x0;
        if (castFrame.isEndHeaders()) value |= 0x4;
        if (castFrame.getPadding().isPadded()) value |= 0x8;
        return value;
    }

    @Override
    public void unmarshalFlagsAndPayload(Http2Frame frame, byte flags, Optional<DataWrapper> maybePayload) {
        PushPromiseFrame castFrame = (PushPromiseFrame) frame;

        castFrame.setEndHeaders((flags & 0x4) == 0x4);
        castFrame.getPadding().setIsPadded((flags & 0x8) == 0x8);

        maybePayload.ifPresent(payload -> {
            List<? extends DataWrapper> split = dataGen.split(payload, 4);
            ByteBuffer prelude = bufferPool.createWithDataWrapper(split.get(0));

            castFrame.setPromisedStreamId(prelude.getInt());
            castFrame.setHeaderFragment(castFrame.getPadding().extractPayloadAndSetPaddingIfNeeded(split.get(1), frame.getStreamId()));
            bufferPool.releaseBuffer(prelude);
        });
    }

}
