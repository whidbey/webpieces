package org.webpieces.router.api;

public interface ObjectStringConverter<T> {

	Class<T> getConverterType();
	
	T stringToObject(String value);
	
	String objectToString(T value);
	
}
