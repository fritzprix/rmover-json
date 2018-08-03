package com.doodream.rmovjs.serde;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;

/**
 *  Converter class defines how object is converted into byte stream (and vice-versa) comprising of
 *  1. conversion of object to a byte array
 *  2. appending delimit marker at the end of the byte array above
 *  3. build @{@link Reader} / @{@link Writer} compatible with converter from {@link InputStream} / @{@link OutputStream}
 */
public interface Converter {

    Reader reader(InputStream inputStream);

    Writer writer(OutputStream outputStream);

    byte[] convert(Object src);

    <T> T invert(byte[] b, Class<T> cls);

    <T> T resolve(Object unresolved, Type type) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
}
