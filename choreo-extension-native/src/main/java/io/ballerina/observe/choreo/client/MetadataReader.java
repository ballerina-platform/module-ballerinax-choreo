package io.ballerina.observe.choreo.client;

/**
 * Read AST metadata from a program.
 *
 * @since 2.0.0
 */
public interface MetadataReader {
    /**
     * Get the AST from a program.
     *
     * @return The JSON string representing the AST
     */
    String getAstData();

    /**
     * Get the hash of AST from ta program.
     *
     * @return The hash of the JSON AST string
     */
    String getAstHash();
}
