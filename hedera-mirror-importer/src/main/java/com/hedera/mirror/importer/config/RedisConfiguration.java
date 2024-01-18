/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hedera.mirror.common.converter.EntityIdDeserializer;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.StreamMessage;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@AutoConfigureBefore(RedisAutoConfiguration.class)
@AutoConfigureAfter({MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
@Configuration
class RedisConfiguration {

    @Bean
    RedisSerializer<StreamMessage> redisSerializer() {
        var module = new SimpleModule();
        module.addDeserializer(EntityId.class, EntityIdDeserializer.INSTANCE);
        module.addSerializer(EntityIdSerializer.INSTANCE);

        var objectMapper = new ObjectMapper(new MessagePackFactory());
        objectMapper.registerModule(module);

        return new Jackson2JsonRedisSerializer<>(objectMapper, StreamMessage.class);
    }

    @Bean
    RedisOperations<String, StreamMessage> redisOperations(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, StreamMessage> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setValueSerializer(redisSerializer());
        return redisTemplate;
    }

    @Bean
    ReactiveRedisOperations<String, StreamMessage> reactiveRedisOperations(ReactiveRedisConnectionFactory factory) {
        var serializationContext = RedisSerializationContext.<String, StreamMessage>newSerializationContext(
                        redisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}
