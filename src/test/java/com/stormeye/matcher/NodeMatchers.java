package com.stormeye.matcher;

import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;

/**
 * Matchers for comparing Node responses to SDK responses.
 *
 * @author ian@meywood.com
 */
public class NodeMatchers {

    /**
     * Matcher that verifies a merkel proof length matches that obtains from the node.
     * <p>
     * The node only provides the length in a text string in the JSON such as: "merkle_proof": "[2160 hex chars]",
     *
     * @param expectedNodeMerkelProof the response from the node as shown above
     * @return the request matcher for merkel proofs
     */
    @SuppressWarnings("rawtypes")
    public static Matcher<String> isValidMerkleProof(final String expectedNodeMerkelProof) {

        //noinspection unchecked
        return new CustomMatcher("Node Merkel proof match") {
            @Override
            public boolean matches(final Object actual) {
                if (actual instanceof String) {
                    return getExpectedLength(expectedNodeMerkelProof) == actual.toString().length();
                }
                return false;
            }

            private int getExpectedLength(final String nodeMerkelProof) {
                return Integer.parseInt(nodeMerkelProof.split(" ")[0].substring(1));
            }
        };
    }
}
