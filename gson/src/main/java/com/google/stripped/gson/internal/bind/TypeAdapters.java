/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.stripped.gson.internal.bind;

import java.io.IOException;
import java.util.HashMap;

import java.util.Map;

import com.google.stripped.gson.Gson;
import com.google.stripped.gson.JsonArray;
import com.google.stripped.gson.JsonElement;
import com.google.stripped.gson.JsonNull;
import com.google.stripped.gson.JsonObject;
import com.google.stripped.gson.JsonPrimitive;
import com.google.stripped.gson.JsonSyntaxException;
import com.google.stripped.gson.TypeAdapter;
import com.google.stripped.gson.TypeAdapterFactory;
import com.google.stripped.gson.annotations.SerializedName;
import com.google.stripped.gson.internal.LazilyParsedNumber;
import com.google.stripped.gson.reflect.TypeToken;
import com.google.stripped.gson.stream.JsonReader;
import com.google.stripped.gson.stream.JsonToken;

/**
 * Type adapters for basic types.
 */
public final class TypeAdapters {
  private TypeAdapters() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("rawtypes")
  public static final TypeAdapter<Class> CLASS         = new TypeAdapter<Class>()
  {

    @Override
    public Class read(JsonReader in) throws IOException
    {
      if (in.peek() == JsonToken.NULL)
      {
        in.nextNull();
        return null;
      }
      else
      {
        throw new UnsupportedOperationException(
                "Attempted to deserialize a java.lang.Class. Forgot to register a type adapter?");
      }
    }
  };

  public static final TypeAdapterFactory CLASS_FACTORY = newFactory(Class.class, CLASS);



  public static final TypeAdapter<Boolean> BOOLEAN = new TypeAdapter<Boolean>() {
    @Override
    public Boolean read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      } else if (in.peek() == JsonToken.STRING) {
        // support strings for compatibility with GSON 1.7
        return Boolean.parseBoolean(in.nextString());
      }
      return in.nextBoolean();
    }

  };

  /**
   * Writes a boolean as a string. Useful for map keys, where booleans aren't
   * otherwise permitted.
   */
  public static final TypeAdapter<Boolean> BOOLEAN_AS_STRING = new TypeAdapter<Boolean>() {
    @Override public Boolean read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return Boolean.valueOf(in.nextString());
    }


  };

  public static final TypeAdapterFactory BOOLEAN_FACTORY
      = newFactory(boolean.class, Boolean.class, BOOLEAN);

  public static final TypeAdapter<Number> INTEGER = new TypeAdapter<Number>() {
    @Override
    public Number read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      try {
        return in.nextInt();
      } catch (NumberFormatException e) {
        throw new JsonSyntaxException(e);
      }
    }

  };
  public static final TypeAdapterFactory INTEGER_FACTORY
      = newFactory(int.class, Integer.class, INTEGER);


  public static final TypeAdapter<String> STRING = new TypeAdapter<String>() {
    @Override
    public String read(JsonReader in) throws IOException {
      JsonToken peek = in.peek();
      if (peek == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      /* coerce booleans to strings for backwards compatibility */
      if (peek == JsonToken.BOOLEAN) {
        return Boolean.toString(in.nextBoolean());
      }
      return in.nextString();
    }

  };

  public static final TypeAdapterFactory STRING_FACTORY = newFactory(String.class, STRING);

  public static final TypeAdapter<JsonElement> JSON_ELEMENT = new TypeAdapter<JsonElement>() {
    @Override public JsonElement read(JsonReader in) throws IOException {
      switch (in.peek()) {
      case STRING:
        return new JsonPrimitive(in.nextString());
      case NUMBER:
        String number = in.nextString();
        return new JsonPrimitive(new LazilyParsedNumber(number));
      case BOOLEAN:
        return new JsonPrimitive(in.nextBoolean());
      case NULL:
        in.nextNull();
        return JsonNull.INSTANCE;
      case BEGIN_ARRAY:
        JsonArray array = new JsonArray();
        in.beginArray();
        while (in.hasNext()) {
          array.add(read(in));
        }
        in.endArray();
        return array;
      case BEGIN_OBJECT:
        JsonObject object = new JsonObject();
        in.beginObject();
        while (in.hasNext()) {
          object.add(in.nextName(), read(in));
        }
        in.endObject();
        return object;
      case END_DOCUMENT:
      case NAME:
      case END_OBJECT:
      case END_ARRAY:
      default:
        throw new IllegalArgumentException();
      }
    }

  };

  public static final TypeAdapterFactory JSON_ELEMENT_FACTORY
      = newTypeHierarchyFactory(JsonElement.class, JSON_ELEMENT);

  private static final class EnumTypeAdapter<T extends Enum<T>> extends TypeAdapter<T> {
    private final Map<String, T> nameToConstant = new HashMap<String, T>();
    private final Map<T, String> constantToName = new HashMap<T, String>();

    public EnumTypeAdapter(Class<T> classOfT) {
      try {
        for (T constant : classOfT.getEnumConstants()) {
          String name = constant.name();
          SerializedName annotation = classOfT.getField(name).getAnnotation(SerializedName.class);
          if (annotation != null) {
            name = annotation.value();
            for (String alternate : annotation.alternate()) {
              nameToConstant.put(alternate, constant);
            }
          }
          nameToConstant.put(name, constant);
          constantToName.put(constant, name);
        }
      } catch (NoSuchFieldException e) {
        AssertionError assertionError = new AssertionError("Missing field in " + classOfT.getName());
        assertionError.initCause(e);
        throw assertionError;
      }
    }
    @Override public T read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return nameToConstant.get(in.nextString());
    }

  }

  public static final TypeAdapterFactory ENUM_FACTORY = new TypeAdapterFactory() {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      Class<? super T> rawType = typeToken.getRawType();
      if (!Enum.class.isAssignableFrom(rawType) || rawType == Enum.class) {
        return null;
      }
      if (!rawType.isEnum()) {
        rawType = rawType.getSuperclass(); // handle anonymous subclasses
      }
      return (TypeAdapter<T>) new EnumTypeAdapter(rawType);
    }
  };

  public static <TT> TypeAdapterFactory newFactory(
      final TypeToken<TT> type, final TypeAdapter<TT> typeAdapter) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked") // we use a runtime check to make sure the 'T's equal
      @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        return typeToken.equals(type) ? (TypeAdapter<T>) typeAdapter : null;
      }
    };
  }

  public static <TT> TypeAdapterFactory newFactory(
      final Class<TT> type, final TypeAdapter<TT> typeAdapter) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked") // we use a runtime check to make sure the 'T's equal
      @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        return typeToken.getRawType() == type ? (TypeAdapter<T>) typeAdapter : null;
      }
      @Override public String toString() {
        return "Factory[type=" + type.getName() + ",adapter=" + typeAdapter + "]";
      }
    };
  }

  public static <TT> TypeAdapterFactory newFactory(
      final Class<TT> unboxed, final Class<TT> boxed, final TypeAdapter<? super TT> typeAdapter) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked") // we use a runtime check to make sure the 'T's equal
      @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();
        return (rawType == unboxed || rawType == boxed) ? (TypeAdapter<T>) typeAdapter : null;
      }
      @Override public String toString() {
        return "Factory[type=" + boxed.getName()
            + "+" + unboxed.getName() + ",adapter=" + typeAdapter + "]";
      }
    };
  }

  public static <TT> TypeAdapterFactory newFactoryForMultipleTypes(final Class<TT> base,
      final Class<? extends TT> sub, final TypeAdapter<? super TT> typeAdapter) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked") // we use a runtime check to make sure the 'T's equal
      @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();
        return (rawType == base || rawType == sub) ? (TypeAdapter<T>) typeAdapter : null;
      }
      @Override public String toString() {
        return "Factory[type=" + base.getName()
            + "+" + sub.getName() + ",adapter=" + typeAdapter + "]";
      }
    };
  }

  /**
   * Returns a factory for all subtypes of {@code typeAdapter}. We do a runtime check to confirm
   * that the deserialized type matches the type requested.
   */
  public static <T1> TypeAdapterFactory newTypeHierarchyFactory(
      final Class<T1> clazz, final TypeAdapter<T1> typeAdapter) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked")
      @Override public <T2> TypeAdapter<T2> create(Gson gson, TypeToken<T2> typeToken) {
        final Class<? super T2> requestedType = typeToken.getRawType();
        if (!clazz.isAssignableFrom(requestedType)) {
          return null;
        }
        return (TypeAdapter<T2>) new TypeAdapter<T1>() {

          @Override public T1 read(JsonReader in) throws IOException {
            T1 result = typeAdapter.read(in);
            if (result != null && !requestedType.isInstance(result)) {
              throw new JsonSyntaxException("Expected a " + requestedType.getName()
                  + " but was " + result.getClass().getName());
            }
            return result;
          }
        };
      }
      @Override public String toString() {
        return "Factory[typeHierarchy=" + clazz.getName() + ",adapter=" + typeAdapter + "]";
      }
    };
  }
}
