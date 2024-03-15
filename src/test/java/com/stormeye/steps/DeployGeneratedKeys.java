package com.stormeye.steps;

import com.casper.sdk.exception.NoSuchTypeException;
import com.casper.sdk.helper.CasperKeyHelper;
import com.casper.sdk.helper.CasperTransferHelper;
import com.casper.sdk.model.common.Ttl;
import com.casper.sdk.model.deploy.Deploy;
import com.casper.sdk.model.deploy.DeployData;
import com.casper.sdk.model.deploy.DeployResult;
import com.casper.sdk.model.key.AlgorithmTag;
import com.casper.sdk.model.key.PublicKey;
import com.casper.sdk.service.CasperService;
import com.stormeye.utils.AssetUtils;
import com.stormeye.utils.CasperClientProvider;
import com.stormeye.utils.ContextMap;
import com.syntifi.crypto.key.AbstractPrivateKey;
import com.syntifi.crypto.key.AbstractPublicKey;
import com.syntifi.crypto.key.Ed25519PrivateKey;
import com.syntifi.crypto.key.Secp256k1PrivateKey;
import dev.oak3.sbs4j.exception.ValueSerializationException;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step Definitions for Deploys with generated keys
 */
public class DeployGeneratedKeys {

    private final ContextMap contextMap = ContextMap.getInstance();
    private final Logger logger = LoggerFactory.getLogger(DeployGeneratedKeys.class);

    @BeforeAll
    public static void setUp() {
        ContextMap.getInstance().clear();
    }


    @Given("that a {string} sender key is generated")
    public void thatSenderKeyIsGenerated(final String algo) throws IOException, GeneralSecurityException, NoSuchTypeException {

        logger.info("that a {} sender key is generated", algo);

        final AbstractPrivateKey sk;
        final AbstractPublicKey pk;

        if (algo.equals("Ed25519")) {
            sk = CasperKeyHelper.createRandomEd25519Key();
            pk = CasperKeyHelper.derivePublicKey((Ed25519PrivateKey) sk);
        } else {
            sk = CasperKeyHelper.createRandomSecp256k1Key();
            pk = CasperKeyHelper.derivePublicKey((Secp256k1PrivateKey) sk);
        }

        assertThat(sk, is(notNullValue()));
        assertThat(pk, is(notNullValue()));

        byte[] msg = "this is the sender".getBytes();
        byte[] signature = sk.sign(msg);
        assertTrue(pk.verify(msg, signature));

        if ("Ed25519".equals(algo)) {
            assertThat(sk.getKey(), is(notNullValue()));
            assertThat(pk.getKey(), is(notNullValue()));
        } else if ("Secp256k1".equals(algo)) {
            assertThat(((Secp256k1PrivateKey) sk).getKeyPair().getPrivateKey(), is(notNullValue()));
            assertThat(pk.getKey(), is(notNullValue()));
        } else {
            throw new NoSuchTypeException("Unknown algorithm");
        }


        contextMap.put(StepConstants.SENDER_KEY_SK, sk);
        contextMap.put(StepConstants.SENDER_KEY_PK, pk);
    }


    @Given("that a {string} receiver key is generated")
    public void thatAReceiverKeyIsGenerated(final String algo) throws IOException, GeneralSecurityException, NoSuchTypeException {

        logger.info("that a {} receiver key is generated", algo);

        final AbstractPublicKey pk;
        final AbstractPrivateKey sk;

        if ("Ed25519".equals(algo)) {
            sk = CasperKeyHelper.createRandomEd25519Key();
            pk = CasperKeyHelper.derivePublicKey((Ed25519PrivateKey) sk);
        } else if ("Secp256k1".equals(algo)) {
            sk = CasperKeyHelper.createRandomSecp256k1Key();
            pk = CasperKeyHelper.derivePublicKey((Secp256k1PrivateKey) sk);
        } else {
            throw new NoSuchTypeException("Unknown algorithm");
        }

        byte[] msg = "this is the receiver".getBytes();
        byte[] signature = sk.sign(msg);
        assertTrue(pk.verify(msg, signature));


        if ("Ed25519".equals(algo)) {
            assertThat(sk.getKey(), is(notNullValue()));
            assertThat(pk.getKey(), is(notNullValue()));
        } else if ("Secp256k1".equals(algo)) {
            assertThat(((Secp256k1PrivateKey) sk).getKeyPair().getPrivateKey(), is(notNullValue()));
            assertThat(pk.getKey(), is(notNullValue()));
        } else {
            throw new NoSuchTypeException("Unknown algorithm");
        }

        contextMap.put(StepConstants.RECEIVER_KEY, pk);
    }

    @And("the key is written to a .pem file")
    public void theKeyIsWrittenToAPemFile() throws IOException {
        final AbstractPrivateKey senderKey = contextMap.get(StepConstants.SENDER_KEY_SK);
        final String path = File.createTempFile("sender_key", ".pem").getPath();

        contextMap.put(StepConstants.SENDER_KEY_SK_PATH, path);
        senderKey.writePrivateKey(path);
    }

    @When("the key is read from the .pem file")
    public void theKeyIsReadFromThePemFile() throws IOException {
        final AbstractPrivateKey senderKey = contextMap.get(StepConstants.SENDER_KEY_SK);
        final AbstractPrivateKey readKey;
        if (senderKey instanceof Ed25519PrivateKey) {
            readKey = new Ed25519PrivateKey();
        } else {
            readKey = new Secp256k1PrivateKey();
        }

        final String path = contextMap.get(StepConstants.SENDER_KEY_SK_PATH);

        readKey.readPrivateKey(path);
        contextMap.put(StepConstants.READ_KEY, readKey);

        // Delete the tmp file
        //noinspection ResultOfMethodCallIgnored
        new File(path).delete();
    }

    @Then("the key is the same as the original key")
    public void theKeyIsTheSameAsTheOriginalKey() {
        final AbstractPrivateKey senderKey = contextMap.get(StepConstants.SENDER_KEY_SK);
        final AbstractPrivateKey readKey = contextMap.get(StepConstants.READ_KEY);
        assertThat(senderKey.getKey(), is(readKey.getKey()));
        // Use the read key as the sender key for the next steps
        contextMap.put(StepConstants.SENDER_KEY, readKey);
    }

    @Then("fund the account from the faucet user with a transfer amount of {long} and a payment amount of {long}")
    public void fundTheAccountFromTheFaucetUserWithATransferAmountOfAndAPaymentAmountOf(long transferAmount, long paymentAmount) throws IOException, NoSuchTypeException, GeneralSecurityException, ValueSerializationException {
        logger.info("fund the account from the faucet user with a transfer amount of {} and a payment amount of {}", transferAmount, paymentAmount);

        final URL faucetPrivateKeyUrl = AssetUtils.getFaucetAsset(1, "secret_key.pem");
        assertThat(faucetPrivateKeyUrl, is(notNullValue()));
        final Ed25519PrivateKey privateKey = new Ed25519PrivateKey();
        privateKey.readPrivateKey(faucetPrivateKeyUrl.getFile());

        contextMap.put(StepConstants.TRANSFER_AMOUNT, transferAmount);
        contextMap.put(StepConstants.PAYMENT_AMOUNT, paymentAmount);

        doDeploy(privateKey, contextMap.get(StepConstants.SENDER_KEY_PK));
    }

    @Then("transfer to the receiver account the transfer amount of {long} and the payment amount of {long}")
    public void transferToTheReceiverAccountTheTransferAmountOfAndThePaymentAmountOf(long transferAmount, long paymentAmount) throws NoSuchTypeException, GeneralSecurityException, ValueSerializationException {
        logger.info("transfer to the receiver account the transfer amount of {} and the payment amount of {}", transferAmount, paymentAmount);

        contextMap.put(StepConstants.TRANSFER_AMOUNT, transferAmount);
        contextMap.put(StepConstants.PAYMENT_AMOUNT, paymentAmount);

        doDeploy(contextMap.get(StepConstants.SENDER_KEY_SK), contextMap.get(StepConstants.RECEIVER_KEY));
    }

    @And("the deploy sender account key contains the {string} algo")
    public void theReturnedBlockHeaderProposerContainsTheAlgo(String algo) {
        logger.info("the deploy sender account key contains the {} algo", algo);
        final DeployResult deployResult = contextMap.get(StepConstants.DEPLOY_RESULT);
        final DeployData deploy = CasperClientProvider.getInstance().getCasperService().getDeploy(deployResult.getDeployHash());
        AlgorithmTag tag = deploy.getDeploy().getApprovals().get(0).getSigner().getTag();
        assertThat(tag.toString().toUpperCase(), is(algo.toUpperCase()));
    }

    private void doDeploy(final AbstractPrivateKey sk, final AbstractPublicKey pk) throws NoSuchTypeException, GeneralSecurityException, ValueSerializationException {

        final Deploy deploy = CasperTransferHelper.buildTransferDeploy(
                sk,
                PublicKey.fromAbstractPublicKey(pk),
                BigInteger.valueOf(contextMap.get(StepConstants.TRANSFER_AMOUNT)),
                "casper-net-1",
                Math.abs(new Random().nextLong()),
                BigInteger.valueOf(contextMap.get(StepConstants.PAYMENT_AMOUNT)),
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>());

        contextMap.put(StepConstants.PUT_DEPLOY, deploy);

        final CasperService casperService = CasperClientProvider.getInstance().getCasperService();

        contextMap.put(StepConstants.DEPLOY_RESULT, casperService.putDeploy(deploy));
    }


}
