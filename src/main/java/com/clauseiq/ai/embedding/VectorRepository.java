package com.clauseiq.ai.embedding;

import com.clauseiq.document.TextChunker.TextChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * pgvector access for contract chunks. Uses plain JDBC because JPA has no native vector type.
 *
 * <p>Tenant isolation: {@code tenantId} is a required parameter of every method and is applied in
 * the SQL WHERE clause <em>before</em> ranking, so another tenant's vectors can never reach the
 * top-K results, the RAG prompt, or the response — regardless of how similar they are.
 */
@Repository
public class VectorRepository {

    private final JdbcTemplate jdbc;

    public VectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveChunks(Long tenantId, Long contractId, List<TextChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings must have the same size");
        }
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk c = chunks.get(i);
            rows.add(new Object[]{tenantId, contractId, c.index(), c.pageNumber(), c.text(), toLiteral(embeddings.get(i))});
        }
        jdbc.batchUpdate("""
                INSERT INTO contract_chunks (tenant_id, contract_id, chunk_index, page_number, text, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                """, rows);
    }

    /**
     * Cosine-similarity search restricted to one tenant (and optionally one of its contracts).
     * The JOIN also requires the contract to belong to the same tenant (defence in depth).
     */
    public List<RetrievedChunk> search(Long tenantId, float[] queryEmbedding, int topK, Long contractId) {
        Objects.requireNonNull(tenantId, "tenantId is required for vector search");
        String vector = toLiteral(queryEmbedding);
        String sql = """
                SELECT ch.id, ch.contract_id, c.original_filename, ch.chunk_index, ch.page_number, ch.text,
                       1 - (ch.embedding <=> ?::vector) AS similarity
                FROM contract_chunks ch
                JOIN contracts c ON c.id = ch.contract_id AND c.tenant_id = ch.tenant_id
                WHERE ch.tenant_id = ?
                  AND ch.embedding IS NOT NULL
                """ + (contractId != null ? " AND ch.contract_id = ?\n" : "") + """
                ORDER BY ch.embedding <=> ?::vector
                LIMIT ?
                """;
        List<Object> params = new ArrayList<>(List.of(vector, tenantId));
        if (contractId != null) {
            params.add(contractId);
        }
        params.add(vector);
        params.add(topK);
        return jdbc.query(sql, (rs, n) -> new RetrievedChunk(
                rs.getLong("id"),
                rs.getLong("contract_id"),
                rs.getString("original_filename"),
                rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_number"),
                rs.getString("text"),
                rs.getDouble("similarity")), params.toArray());
    }

    public void deleteByContract(Long tenantId, Long contractId) {
        jdbc.update("DELETE FROM contract_chunks WHERE tenant_id = ? AND contract_id = ?", tenantId, contractId);
    }

    static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
