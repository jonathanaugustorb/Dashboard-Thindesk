package com.pfc;

import com.pfc.thindesk.entity.StatusChamado;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StatusChamadoReadingConverter()));
    }

    @ReadingConverter
    static class StatusChamadoReadingConverter implements Converter<String, StatusChamado> {

        @Override
        public StatusChamado convert(String valor) {
            return StatusChamado.de(valor);
        }
    }
}
