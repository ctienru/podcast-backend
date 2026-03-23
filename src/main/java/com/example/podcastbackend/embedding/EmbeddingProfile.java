package com.example.podcastbackend.embedding;

public enum EmbeddingProfile {
    ZH,   // Chinese (zh-tw + zh-cn share the same model)
    EN,   // English
    NONE  // BM25-only mode, no embedding needed
}
