package com.webpieces.http2parser.api.dto;

import com.webpieces.http2parser.api.dto.lib.AbstractHttp2Frame;
import com.webpieces.http2parser.api.dto.lib.Http2FrameType;
import com.webpieces.http2parser.api.dto.lib.Http2MsgType;
import com.webpieces.http2parser.api.dto.lib.StreamMsg;
import com.webpieces.http2parser.api.dto.lib.PriorityDetails;

public class PriorityFrame extends AbstractHttp2Frame implements StreamMsg {

    /* flags */

    /* payload */
    private PriorityDetails priorityDetails = new PriorityDetails();

    public PriorityFrame() {
	}

    public PriorityFrame(int streamId, PriorityDetails details) {
    	super(streamId);
    	this.priorityDetails = details;
	}
    
    public PriorityDetails getPriorityDetails() {
        return priorityDetails;
    }

    public void setPriorityDetails(PriorityDetails priorityDetails) {
        this.priorityDetails = priorityDetails;
    }
    
    @Override
    public Http2FrameType getFrameType() {
        return Http2FrameType.PRIORITY;
    }
    
	@Override
	public boolean isEndOfStream() {
		return false;
	}
	
	@Override
	public Http2MsgType getMessageType() {
		return Http2MsgType.PRIORITY;
	}
	
    @Override
    public String toString() {
        return "PriorityFrame{" +
        		super.toString() +
                "priorityDetails=" + priorityDetails +
                "} ";
    }

}
