package org.webpieces.devrouter.impl;

import org.webpieces.ctx.api.Current;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.actions.Actions;

public class NotFoundController {

	public Action notFound() {
		RouterRequest request = Current.request();
		String error = request.multiPartFields.get("webpiecesError");
		return Actions.renderThis("error", error);
	}
}
