package com.doodream.rmovjs.util;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Types {
    private static final Logger Log = LoggerFactory.getLogger(Types.class);
    private static final String CLASS_PREFIX = "class ";
    private static final String INTERFACE_PREFIX = "interface ";
    private static Pattern INTERNAL_TYPE_SELECTOR_PATTERN = Pattern.compile("([^\\<\\>]+)\\<([\\s\\S]+)\\>");

    public static Type[] unwrapType(String typeName) throws ClassNotFoundException, IllegalArgumentException {
        Matcher matcher = INTERNAL_TYPE_SELECTOR_PATTERN.matcher(typeName);
        if (matcher.matches()) {
            // try unwrap recursive
            String unwrapped = matcher.group(2);
            String[] split = splitTypeParameter(unwrapped);
            List<Type> types = new ArrayList<>();
            for (String s : split) {
                try {
                    final Type[] parameters = unwrapType(s);
                    matcher = INTERNAL_TYPE_SELECTOR_PATTERN.matcher(s);
                    if(matcher.matches()) {
                        final Type rawClass = Class.forName(matcher.group(1));
                        types.add(getType((Class<?>) rawClass, parameters));
//                        types.add(new ParameterizedType() {
//                            @Override
//                            public Type[] getActualTypeArguments() {
//                                return parameters;
//                            }
//
//                            @Override
//                            public Type getRawType() {
//                                return rawClass;
//                            }
//
//                            @Override
//                            public Type getOwnerType() {
//                                return null;
//                            }
//                        });
                    }
                } catch (IllegalArgumentException e) {
                    types.add(Class.forName(s));
                }
            }
            return types.toArray(new Type[0]);
        } else {
            throw new IllegalArgumentException("Nothing to unwrap");
        }
    }

    /**
     * split type parameter at outermost scope
     * for example -> Map<Map<String,String>, Map<String,String>>, String
     *                                             split here -> |
     * @param original typeName from {@link Type}
     * @return if there is split return type names as string array, otherwise array contains original
     */
    private static String[] splitTypeParameter(String original) {
        int pos;
        String chunk = original.trim();
        StringBuilder lineSeparatedParameterBuilder = new StringBuilder();
        while (((pos = findNextTypeParameterIndex(chunk)) > 0)) {
            lineSeparatedParameterBuilder.append(chunk, 0, pos);
            lineSeparatedParameterBuilder.append("\n");
            chunk = chunk.substring(pos + 1);
        }
        lineSeparatedParameterBuilder.append(chunk.trim());
        lineSeparatedParameterBuilder.append("\n");
        return lineSeparatedParameterBuilder.toString().split("\n");
    }

    private static int findNextTypeParameterIndex(String original) {
        int pos = 0,i;
        final int len = original.length();
        for (i = 0; i < len; i++) {
            switch (original.charAt(i)) {
                case '<':
                    pos++;
                    break;
                case '>':
                    pos--;
                    break;
                case ',':
                    if(pos == 0) {
                        return i;
                    }
                    break;
                default:
                    break;
            }
        }
        return 0;
    }

    public static <T> boolean isCastable(T body, Class<?> rawCls) {
        try {
            rawCls.cast(body);
            return true;
        } catch (ClassCastException ignored) {
        }
        return false;
    }

    public static  <T> boolean isCastable(T body, Type type) {
        try {
            ((Class) type).cast(body);
            return true;
        } catch (ClassCastException ignored) {
        }
        return false;

    }

    public static Type getType(Class<?> rawCls, Type ...typeParameter) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return typeParameter;
            }

            @Override
            public Type getRawType() {
                return rawCls;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    public static String getTypeName(Type type) {
        final String clsName = type.toString();
        if(clsName.startsWith(CLASS_PREFIX)) {
            return clsName.replace(CLASS_PREFIX,"");
        }
        if(clsName.startsWith(INTERFACE_PREFIX)) {
            return clsName.replace(INTERFACE_PREFIX, "");
        }
        return clsName;
    }

    public static boolean isPrimitive(Type type) {
        switch (type.getTypeName()) {
            case "float":
            case "int":
            case "double":
            case "long":
            case "boolean":
                return true;
        }
        return false;
    }

    public static <T> Object primitive(T value, Type type) {
        switch (type.getTypeName()) {
            case "float":
                return Float.parseFloat(value.toString());
            case "int":
                return Integer.parseInt(value.toString());
            case "double":
                return Double.parseDouble(value.toString());
            case "long":
                return Long.parseLong(value.toString());
            case "boolean":
                return Boolean.parseBoolean(value.toString());
        }
        return value;
    }
}
