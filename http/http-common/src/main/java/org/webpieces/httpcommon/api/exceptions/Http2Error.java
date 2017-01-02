package org.webpieces.httpcommon.api.exceptions;

import java.util.List;

import com.webpieces.http2parser.api.dto.lib.Http2Frame;

public abstract class Http2Error extends RuntimeException {
    public abstract List<Http2Frame> toFrames();
}

