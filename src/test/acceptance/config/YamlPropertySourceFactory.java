package acceptance.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * Lets {@link org.springframework.context.annotation.PropertySource @PropertySource}
 * load {@code .yml} / {@code .yaml} files, which Spring otherwise refuses to do
 * (it only handles {@code .properties} out of the box).
 *
 * Used by {@link AcceptanceTestConfig} to ingest {@code application-test.yml}
 * into the test Environment so @ConfigurationProperties beans bind correctly.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(@Nullable String name, EncodedResource encodedResource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(encodedResource.getResource());
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        String sourceName = name != null
                ? name
                : Objects.requireNonNull(encodedResource.getResource().getFilename(), "yaml resource has no filename");
        return new PropertiesPropertySource(sourceName, properties);
    }
}
