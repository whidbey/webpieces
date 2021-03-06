package org.webpieces.router.impl.actions;

import java.util.HashMap;
import java.util.Map;

public class PageArgListConverter {

	public static Map<String, Object> createPageArgMap(Object... pageArgTupleList) {
		Map<String, Object> temp = new HashMap<>();
		
		if(pageArgTupleList.length % 2 != 0)
			throw new IllegalArgumentException("All arguments to render must be even with String, Object, String, Object (ie. key, value, key, value)");
		
		String key = null;
		for(int i = 0; i < pageArgTupleList.length; i++) {
			Object obj = pageArgTupleList[i];
			if(i % 2 == 0) {
				if(obj == null) 
					throw new IllegalArgumentException("Argument at position="+i+" cannot be null since it is a key and must be of type String");
				else if(!(obj instanceof String))
					throw new IllegalArgumentException("Argument at position="+i+" must be a String and wasn't since it is a key.  obj.toString=="+obj);
				key = (String)obj;
			} else {
				temp.put(key, obj);
			}
		}
		return temp;
	}

}
