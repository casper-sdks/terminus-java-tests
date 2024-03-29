package com.stormeye.utils;

import com.casper.sdk.model.key.PublicKey;
import com.syntifi.crypto.key.AbstractPrivateKey;
import com.syntifi.crypto.key.Ed25519PrivateKey;
import com.syntifi.crypto.key.Ed25519PublicKey;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Utility class for obtaining resources from the assets folder
 *
 * @author ian@meywood.com
 */
public class AssetUtils {

    public static URL getUserKeyAsset(final int networkId, final int userId, final String keyFilename) {
        String path = String.format("/net-%d/user-%d/%s", networkId, userId, keyFilename);
        return Objects.requireNonNull(AssetUtils.class.getResource(path), "missing resource " + path);
    }

    public static URL getFaucetAsset(final int networkId, final String keyFilename) {
        String path = String.format("/net-%d/faucet/%s", networkId, keyFilename);
        return Objects.requireNonNull(AssetUtils.class.getResource(path), "missing resource " + path);
    }

    public static URL getStandardTestResourceURL(String jsonFilename) throws MalformedURLException {
        final Path currentRelativePath = Paths.get("");
        final String jsonPath = "file://" + currentRelativePath.toAbsolutePath() + "/terminus-test-resources/" + jsonFilename;
        return new URL(jsonPath);
    }

    public static PublicKey getFaucetPublicKey() throws IOException {
        return PublicKey.fromAbstractPublicKey(getFaucetPrivateKey().derivePublicKey());
    }

    public static AbstractPrivateKey getFaucetPrivateKey() throws IOException {
        final URL faucetPrivateKeyUrl = getFaucetAsset(1, "secret_key.pem");
        assertThat(faucetPrivateKeyUrl, is(notNullValue()));
        final Ed25519PrivateKey privateKey = new Ed25519PrivateKey();
        privateKey.readPrivateKey(faucetPrivateKeyUrl.getFile());
        return privateKey;
    }

    public static PublicKey getUserPublicKey(final int userId) throws IOException {
        final URL user1KeyUrl = getUserKeyAsset(1, userId, "public_key.pem");
        final Ed25519PublicKey publicKey = new Ed25519PublicKey();
        publicKey.readPublicKey(user1KeyUrl.getFile());
        return PublicKey.fromAbstractPublicKey(publicKey);
    }

    public static AbstractPrivateKey getUserPrivateKey(final int userId) throws IOException {
        final URL userKeyUrl = getUserKeyAsset(1, userId, "secret_key.pem");
        final Ed25519PrivateKey privateKey = new Ed25519PrivateKey();
        privateKey.readPrivateKey(userKeyUrl.getFile());
        return privateKey;
    }
}
