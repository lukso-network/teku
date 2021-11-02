/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.restapi.openapi;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import tech.pegasys.teku.infrastructure.restapi.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

public class OpenApiResponse {
  private final String description;
  private final Map<String, SerializableTypeDefinition<?>> content;

  public OpenApiResponse(
      final String description, final Map<String, SerializableTypeDefinition<?>> content) {
    this.description = description;
    this.content = content;
  }

  public void writeOpenApi(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("description", description);
    gen.writeObjectFieldStart("content");
    for (Entry<String, SerializableTypeDefinition<?>> contentEntry : content.entrySet()) {
      gen.writeObjectFieldStart(contentEntry.getKey());
      gen.writeFieldName("schema");
      contentEntry.getValue().serializeOpenApiTypeOrReference(gen);
      gen.writeEndObject();
    }

    gen.writeEndObject();
    gen.writeEndObject();
  }

  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return content.values().stream()
        .flatMap(type -> type.getSelfAndReferencedTypeDefinitions().stream())
        .collect(toSet());
  }
}
