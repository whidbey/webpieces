package com.webpieces.httpparser2.api;

import java.util.List;

import com.webpieces.httpparser2.api.dto.Http2Frame;

public interface Memento {

	ParsedStatus getStatus();

	/**
	 * In the case where you pass in bytes 2 or more messages, we
	 * give you back all the parsed messages so far
	 * @return
	 */
	List<Http2Frame> getParsedMessages();
	
}