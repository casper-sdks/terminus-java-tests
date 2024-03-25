package com.stormeye.matcher;

import com.casper.sdk.model.event.DataType;
import com.casper.sdk.model.event.Event;
import com.casper.sdk.model.event.blockadded.BlockAdded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Matchers for block added events
 *
 * @author ian@meywood.com
 */
public class BlockAddedMatchers {

    private static final Logger logger = LoggerFactory.getLogger(BlockAddedMatchers.class);


    @SuppressWarnings("rawtypes")
    public static class TransferHashMatcher extends ExpiringMatcher.PreCheckMatcher<Event> {
        final Map<String, Event<?>> transferHashes = new HashMap<>();
        private final Supplier<String> expectedTransferHash;
        private final OnMatch<Event<?>> onMatch;

        public TransferHashMatcher(final Supplier<String> expectedTransferHash,
                                   final OnMatch<Event<?>> onMatch) {
            super("TransferHashMatcher");
            this.expectedTransferHash = expectedTransferHash;
            this.onMatch = onMatch;
        }


        @Override
        public boolean matches(final Object actual) {

            if (actual instanceof Event && ((Event<?>) actual).getDataType() == DataType.BLOCK_ADDED) {
                //noinspection unchecked
                final Event<BlockAdded> event = (Event<BlockAdded>) actual;
                if (event.getDataType() == DataType.BLOCK_ADDED) {
                    final BlockAdded blockAdded = event.getData();

                    final String deployHashes = blockAdded.getBlock().getBody().getDeployHashes()
                            .stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));

                    blockAdded.getBlock().getBody().getTransferHashes()
                            .stream()
                            .map(Object::toString)
                            .forEach(hash -> transferHashes.put(hash, event));

                    logger.info("Block added deploy hashes: [{}], transfer hashes: [{}]", deployHashes, transferHashes);

                    return hasMatch(event);
                }
            }
            return false;
        }

        private boolean hasMatch(Event<BlockAdded> event) {
            if (expectedTransferHash.get() != null && transferHashes.containsKey(expectedTransferHash.get())) {
                if (onMatch != null) {
                    onMatch.onMatch(event);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean hasMatch() {
            if (expectedTransferHash.get() != null && transferHashes.containsKey(expectedTransferHash.get())) {
                if (onMatch != null) {
                    onMatch.onMatch(getMatch());
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Event getMatch() {
            return transferHashes.get(expectedTransferHash.get());
        }
    }

    public static ExpiringMatcher<Event<?>> hasTransferHashWithin(final Supplier<String> expectedTransferHash,
                                                                  final OnMatch<Event<?>> onMatch) {
        //noinspection unchecked,rawtypes
        return new ExpiringMatcher(new TransferHashMatcher(expectedTransferHash, onMatch));


    }
}
