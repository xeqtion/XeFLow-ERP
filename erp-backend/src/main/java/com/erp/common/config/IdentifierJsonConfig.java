package com.erp.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** Keep database identifiers lossless across the HTTP JSON / JavaScript boundary. */
@Configuration
public class IdentifierJsonConfig {

    private static final JsonSerializer<Object> ID_COLLECTION_SERIALIZER = new JsonSerializer<>() {
        @Override
        public void serialize(Object value, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartArray(value);
            for (Object id : (Collection<?>) value) {
                if (id == null) {
                    provider.defaultSerializeNull(json);
                } else {
                    json.writeString(id.toString());
                }
            }
            json.writeEndArray();
        }
    };

    @Bean
    public Module identifierJsonModule() {
        SimpleModule module = new SimpleModule("erp-identifiers-as-strings");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                            BeanDescription bean,
                                                            List<BeanPropertyWriter> properties) {
                if (!bean.getBeanClass().getPackageName().startsWith("com.erp.")) {
                    return properties;
                }
                for (BeanPropertyWriter property : properties) {
                    if (property.hasSerializer()) {
                        continue; // Keep explicit field-level serializers.
                    }
                    JavaType type = property.getType();
                    String name = property.getName();
                    if (isLong(type) && isIdentifier(name)) {
                        property.assignSerializer(ToStringSerializer.instance);
                    } else if (name.endsWith("Ids") && type.isCollectionLikeType()
                            && isLong(type.getContentType())) {
                        property.assignSerializer(ID_COLLECTION_SERIALIZER);
                    }
                }
                return properties;
            }
        });
        return module;
    }

    private static boolean isLong(JavaType type) {
        return type != null && (type.hasRawClass(Long.class) || type.hasRawClass(long.class));
    }

    private static boolean isIdentifier(String name) {
        return name.equals("id") || name.endsWith("Id")
                || name.equals("createBy") || name.equals("updateBy");
    }
}
