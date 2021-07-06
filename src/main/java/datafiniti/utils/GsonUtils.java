package datafiniti.utils;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class GsonUtils {
	private static class AtomicFloatTypeAdapter implements JsonSerializer<AtomicFloat>, JsonDeserializer<AtomicFloat> {
		@Override
		public JsonElement serialize(AtomicFloat src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(src.get());
		}

		@Override
		public AtomicFloat deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			float floatValue = json.getAsFloat();
			return new AtomicFloat(floatValue);
		}
	}

	public final static Gson gson = new GsonBuilder().registerTypeAdapter(AtomicFloat.class, new AtomicFloatTypeAdapter())
			.create();
}
