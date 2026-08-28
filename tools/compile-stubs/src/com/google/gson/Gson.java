package com.google.gson;

import java.io.Reader;
import java.io.Writer;

public class Gson {
    public <T> T fromJson(Reader reader, Class<T> type) { return null; }
	public <T> T fromJson(String json, Class<T> type) { return null; }
	public String toJson(Object value) { return ""; }
    public void toJson(Object value, Writer writer) {}
}
