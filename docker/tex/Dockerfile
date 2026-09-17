# Sandbox image used to compile LLM-generated resume markup into PDF.
#
# The backend never runs LaTeX on the host: LaTeX is a full programming language
# that can read and write files, so markup produced by an LLM is compiled inside
# a throwaway container with no network and a memory cap. See DockerTexCompiler.
#
# Base tag is intentionally "medium": it carries the classes most resume templates
# need without the multi-gigabyte full install. If a template pulls in a missing
# class, rebuild with `texlive/texlive:latest` (see TEX_BASE_IMAGE in .env.example).
FROM texlive/texlive:latest-medium

# pandoc converts Markdown resumes to PDF via xelatex, so a single image serves
# both supported resume formats.
RUN apt-get update \
 && apt-get install -y --no-install-recommends pandoc \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /work
