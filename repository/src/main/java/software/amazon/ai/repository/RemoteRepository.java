/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package software.amazon.ai.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import software.amazon.ai.util.Utils;

public class RemoteRepository implements Repository {

    private static final long ONE_DAY = Duration.ofDays(1).toMillis();

    private String name;
    private URI uri;

    public RemoteRepository(String name, URI uri) {
        this.name = name;
        this.uri = uri;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public URI getBaseUri() {
        return uri;
    }

    @Override
    public Metadata locate(MRL mrl) throws IOException {
        URI mrlUri = mrl.toURI();
        URI file = uri.resolve(mrlUri.getPath() + "/metadata.json");
        Path cacheDir = getCacheDirectory().resolve(mrlUri.getPath());
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        Path cacheFile = cacheDir.resolve("metadata.json");
        if (Files.exists(cacheFile)) {
            try (Reader reader = Files.newBufferedReader(cacheFile)) {
                Metadata metadata = GSON.fromJson(reader, Metadata.class);
                Date lastUpdated = metadata.getLastUpdated();
                if (System.currentTimeMillis() - lastUpdated.getTime() < ONE_DAY) {
                    metadata.setRepositoryUri(mrlUri);
                    return metadata;
                }
            }
        }

        try (InputStream is = file.toURL().openStream()) {
            String json = Utils.toString(is);
            Metadata metadata = GSON.fromJson(json, Metadata.class);
            metadata.setLastUpdated(new Date());
            try (Writer writer = Files.newBufferedWriter(cacheFile)) {
                writer.write(GSON.toJson(metadata));
            }
            metadata.setRepositoryUri(mrlUri);
            return metadata;
        }
    }

    @Override
    public Artifact resolve(MRL mrl, String version, Map<String, String> filter)
            throws IOException {
        Metadata metadata = locate(mrl);
        VersionRange range = VersionRange.parse(version);
        List<Artifact> artifacts = metadata.search(range, filter);
        if (artifacts.isEmpty()) {
            return null;
        }
        // TODO: find hightest version.
        return artifacts.get(0);
    }
}
