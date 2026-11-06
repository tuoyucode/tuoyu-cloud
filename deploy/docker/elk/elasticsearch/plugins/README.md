# Elasticsearch plugins

Elasticsearch plugins must match the exact Elasticsearch version.

The Docker Compose file mounts `/docker/elk/elasticsearch/plugins`
for Elasticsearch 8.19.16. Do not reuse a 7.x plugin directory with 8.x,
otherwise Elasticsearch will fail during startup.

If IK analysis is required, install an IK package built for Elasticsearch
8.19.16 into that host directory.
