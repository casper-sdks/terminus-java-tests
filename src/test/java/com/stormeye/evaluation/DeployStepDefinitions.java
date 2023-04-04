package com.stormeye.evaluation;

import com.casper.sdk.helper.CasperTransferHelper;
import com.casper.sdk.identifier.block.HashBlockIdentifier;
import com.casper.sdk.model.block.JsonBlockData;
import com.casper.sdk.model.clvalue.CLValuePublicKey;
import com.casper.sdk.model.clvalue.CLValueU512;
import com.casper.sdk.model.clvalue.cltype.CLTypeU512;
import com.casper.sdk.model.common.Digest;
import com.casper.sdk.model.common.Ttl;
import com.casper.sdk.model.deploy.*;
import com.casper.sdk.model.deploy.executabledeploy.ExecutableDeployItem;
import com.casper.sdk.model.event.Event;
import com.casper.sdk.model.event.EventType;
import com.casper.sdk.model.event.blockadded.BlockAdded;
import com.casper.sdk.model.event.deployaccepted.DeployAccepted;
import com.casper.sdk.model.key.PublicKey;
import com.casper.sdk.service.CasperService;
import com.stormeye.event.EventHandler;
import com.stormeye.matcher.DeployMatchers;
import com.stormeye.matcher.ExpiringMatcher;
import com.stormeye.utils.AssetUtils;
import com.stormeye.utils.CasperClientProvider;
import com.stormeye.utils.ParameterMap;
import com.syntifi.crypto.key.Ed25519PrivateKey;
import com.syntifi.crypto.key.Ed25519PublicKey;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.*;

import static com.stormeye.evaluation.BlockAddedMatchers.hasTransferHashWithin;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Step Definitions for Deploy Cucumber Tests.
 *
 * @author ian@meywood.com
 */
public class DeployStepDefinitions {

    private static final ParameterMap parameterMap = ParameterMap.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(DeployStepDefinitions.class);
    private static EventHandler eventHandler;

    @BeforeAll
    public static void setUp() {
        parameterMap.clear();
        eventHandler = new EventHandler();
    }

    @SuppressWarnings("unused")
    @AfterAll
    void tearDown() {
        eventHandler.close();
    }

    @Given("that user-{int} initiates a transfer to user-{int}")
    public void thatUserCreatesATransferOfToUser(final int senderId, final int receiverId) throws IOException {

        logger.info("Given that user-{} initiates a transfer to user-{} ", senderId, receiverId);

        final Ed25519PrivateKey senderKey = new Ed25519PrivateKey();
        final Ed25519PublicKey receiverKey = new Ed25519PublicKey();

        senderKey.readPrivateKey(AssetUtils.getUserKeyAsset(1, senderId, "secret_key.pem").getFile());
        receiverKey.readPublicKey(AssetUtils.getUserKeyAsset(1, receiverId, "public_key.pem").getFile());

        parameterMap.put("senderKey", senderKey);
        parameterMap.put("receiverKey", receiverKey);
    }

    @And("the deploy is given a ttl of {int}m")
    public void theDeployIsGivenATtlOfM(final int ttlMinutes) {

        logger.info("And the deploy has a ttl of {}m", ttlMinutes);

        parameterMap.put("ttl", Ttl.builder().ttl(ttlMinutes + "m").build());
    }

    @And("the transfer amount is {long}")
    public void theTransferAmountIs(final long amount) {

        logger.info("And the transfer amount is {}", amount);

        parameterMap.put("transferAmount", BigInteger.valueOf(amount));
    }

    @And("the transfer gas price is {long}")
    public void theTransferPriceIs(final long price) {

        logger.info("And the transfer gas price is {}", price);

        parameterMap.put("gasPrice", price);
    }

    @When("the deploy is put on chain {string}")
    public void theDeployIsPut(final String chainName) throws Exception {

        logger.info("When the deploy is put on chain {}", chainName);

        final Date timestamp = new Date();
        parameterMap.put("deploy-timestamp", timestamp);

        final Deploy deploy = CasperTransferHelper.buildTransferDeploy(
                parameterMap.get("senderKey"),
                PublicKey.fromAbstractPublicKey(parameterMap.get("receiverKey")),
                parameterMap.get("transferAmount"),
                chainName,
                Math.abs(new Random().nextLong()),
                BigInteger.valueOf(100000000L),
                parameterMap.get("gasPrice"),
                parameterMap.get("ttl"),
                timestamp,
                new ArrayList<>());

        parameterMap.put("put-deploy", deploy);


        final CasperService casperService = CasperClientProvider.getInstance().getCasperService();

        parameterMap.put("deployResult", casperService.putDeploy(deploy));
    }


    @Then("the deploy response contains a valid deploy hash of length {int} and an API version {string}")
    public void theValidDeployHashIsReturned(final int hashLength, final String apiVersion) {

        logger.info("Then the deploy response contains a valid deploy hash of length {} and an API version {}", hashLength, apiVersion);

        DeployResult deployResult = parameterMap.get("deployResult");
        assertThat(deployResult, is(notNullValue()));
        assertThat(deployResult.getDeployHash(), is(notNullValue()));
        assertThat(deployResult.getDeployHash().length(), is((hashLength)));
        assertThat(deployResult.getApiVersion(), is((apiVersion)));

        logger.info("deployResult.getDeployHash() {}", deployResult.getDeployHash());
    }

    @Then("wait for a block added event with a timout of {long} seconds")
    public void waitForABlockAddedEventWithATimoutOfSeconds(final long timeout) throws Exception {

        logger.info("Then wait for a block added event with a timout of {} seconds", timeout);

        final DeployResult deployResult = parameterMap.get("deployResult");

        final ExpiringMatcher<Event<BlockAdded>> matcher = (ExpiringMatcher<Event<BlockAdded>>) eventHandler.addEventMatcher(
                EventType.MAIN,
                hasTransferHashWithin(
                        deployResult.getDeployHash(),
                        blockAddedEvent -> parameterMap.put("lastBlockAdded", blockAddedEvent.getData())
                )
        );

        assertThat(matcher.waitForMatch(timeout), is(true));

        eventHandler.removeEventMatcher(EventType.MAIN, matcher);

        final Digest matchingBlockHash = ((BlockAdded) parameterMap.get("lastBlockAdded")).getBlockHash();
        assertThat(matchingBlockHash, is(notNullValue()));

        final JsonBlockData block = CasperClientProvider.getInstance().getCasperService().getBlock(new HashBlockIdentifier(matchingBlockHash.toString()));
        assertThat(block, is(notNullValue()));
        final List<String> transferHashes = block.getBlock().getBody().getTransferHashes();
        assertThat(transferHashes, hasItem(deployResult.getDeployHash()));
    }

    @And("the Deploy is accepted")
    public void theDeployIsAccepted() throws Exception {

        final DeployResult deployResult = parameterMap.get("deployResult");
        logger.info("the Deploy {} is accepted", deployResult.getDeployHash());

        final ExpiringMatcher<Event<DeployAccepted>> matcher = (ExpiringMatcher<Event<DeployAccepted>>) eventHandler.addEventMatcher(
                EventType.DEPLOYS,
                DeployMatchers.theDeployIsAccepted(
                        deployResult.getDeployHash(),
                        event -> parameterMap.put("deployAccepted", event.getData())
                )
        );

        assertThat(matcher.waitForMatch(5000L), is(true));

        eventHandler.removeEventMatcher(EventType.DEPLOYS, matcher);
    }

    @Given("that a Transfer has been successfully deployed")
    public void thatATransferHasBeenDeployed() {

        logger.info("Given that a Transfer has been deployed");

        final DeployResult deployResult = parameterMap.get("deployResult");
        assertThat(deployResult, is(notNullValue()));
    }

    @When("a deploy is requested via the info_get_deploy RCP method")
    public void whenTheDeployIsRequestedAValidDeployDataIsReturned() {

        final DeployResult deployResult = parameterMap.get("deployResult");
        final CasperService casperService = CasperClientProvider.getInstance().getCasperService();
        final DeployData deploy = casperService.getDeploy(deployResult.getDeployHash());
        assertThat(deploy, is(notNullValue()));
        parameterMap.put("info_get_deploy", deploy);
        assertThat(deploy.getExecutionResults().size(), is(greaterThan(0)));
    }

    @Then("the deploy data has an API version of {string}")
    public void theDeployDataHasAnAPIVersionOf(final String apiVersion) {

        logger.info("Then the deploy data has an API version of {}", apiVersion);

        assertThat(getDeployData().getApiVersion(), is(apiVersion));
    }

    @And("the deploy has a session type of {string}")
    public void theDeploySSessionIsA(final String sessionType) {
        final String actualSessionType = getDeployData().getDeploy().getSession().getClass().getSimpleName().toLowerCase();
        final String expectedSessionType = sessionType.replace(" ", "").toLowerCase();
        assertThat(actualSessionType, is(expectedSessionType));
    }

    @And("the deploy is approved by user-{int}")
    public void theDeployIsSignedByUser(final int userId) throws IOException {

        final List<Approval> approvals = getDeployData().getDeploy().getApprovals();
        assertThat(approvals, hasSize(1));

        final Ed25519PublicKey approvalKey = new Ed25519PublicKey();
        final URL userKeyAsset = AssetUtils.getUserKeyAsset(1, userId, "public_key.pem");
        approvalKey.readPublicKey(userKeyAsset.getFile());
        PublicKey publicKey = PublicKey.fromAbstractPublicKey(approvalKey);

        assertThat(approvals.get(0).getSigner(), is(publicKey));
    }

    @And("the deploy execution result has {string} block hash")
    public void theDeployExecutionResultHasBlockHash(final String blockName) {

        final BlockAdded blockAdded = parameterMap.get(blockName);
        assertThat(
                getDeployData().getExecutionResults().get(0).getBlockHash(),
                is(blockAdded.getBlockHash().toString())
        );
    }

    @And("the deploy execution has a cost of {long} motes")
    public void theDeployExecutionResultHasACostOf(final long cost) {
        assertThat(getDeployData().getExecutionResults().get(0).getResult().getCost(), is(BigInteger.valueOf(cost)));
    }

    @And("the deploy header has a gas price of {long}")
    public void theDeployHeaderHasAGasPriceOf(final long gasPrice) {
        assertThat(getDeployData().getDeploy().getHeader().getGasPrice(), is(gasPrice));
    }

    @And("the deploy header has a chain name of {string}")
    public void theDeployHeaderHasAChainNameOf(final String chainName) {
        assertThat(getDeployData().getDeploy().getHeader().getChainName(), is(chainName));
    }

    @And("the deploy has a gas price of {long}")
    public void theDeployHasAGasPriceOf(long gasPrice) {
        assertThat(getDeployData().getDeploy().getHeader().getGasPrice(), is(gasPrice));
    }

    @And("the deploy session has a {string} argument value of type {string}")
    public void theDeploySessionHasAArgumentOfType(final String argName, final String argType) {
        final ExecutableDeployItem session = getDeployData().getDeploy().getSession();
        final NamedArg<?> namedArg = getNamedArg(session.getArgs(), argName);
        assertThat(namedArg.getClValue().getClass().getSimpleName(), is("CLValue" + argType));
    }

    @And("the deploy session has a {string} argument with a numeric value of {long}")
    public void theDeploySessionHasAArgumentWithAValueOf(String argName, long value) {
        final ExecutableDeployItem session = getDeployData().getDeploy().getSession();
        final NamedArg<?> namedArg = getNamedArg(session.getArgs(), argName);
        if (namedArg.getClValue().getClType() instanceof CLTypeU512) {
            assertThat(namedArg.getClValue().getValue(), is(BigInteger.valueOf(value)));
        } else {
            throw new IllegalArgumentException(namedArg.getClValue().getClType().getClass().getSimpleName() + " not yet implemented");
        }
    }

    @And("the deploy session has a {string} argument with the public key of user-{int}")
    public void theDeploySessionHasAArgumentWithThePublicKeyOfUser(String argName, int userId) throws IOException {
        final ExecutableDeployItem session = getDeployData().getDeploy().getSession();

        final Ed25519PublicKey publicKey = new Ed25519PublicKey();
        publicKey.readPublicKey(AssetUtils.getUserKeyAsset(1, userId, "public_key.pem").getFile());

        final NamedArg<?> namedArg = getNamedArg(session.getArgs(), argName);
        final CLValuePublicKey clValue = (CLValuePublicKey) namedArg.getClValue();
        assertThat(clValue.getValue(), is(PublicKey.fromAbstractPublicKey(publicKey)));
    }

    @And("the deploy has a ttl of {int}m")
    public void theDeployHasATtlOfM(int ttlMinutes) {
        assertThat(getDeployData().getDeploy().getHeader().getTtl().getTtl(), is(Ttl.builder().ttl(ttlMinutes + "m").build().getTtl()));
    }

    @And("the deploy has a valid body hash")
    public void theDeployHasAValidBodyHash() {
        assertThat(getDeployData().getDeploy().getHeader().getBodyHash().isValid(), is(true));

        // Compare body hash of put deploy with
        final Deploy deploy = parameterMap.get("put-deploy");
        assertThat(deploy.getHeader().getBodyHash(), is(deploy.getHeader().getBodyHash()));
    }

    @And("the deploy has a payment amount of {long}")
    public void theDeployHasAPaymentAmountOf(long amount) {
        final NamedArg<?> amountArg = getNamedArg(getDeployData().getDeploy().getPayment().getArgs(), "amount");
        assertThat(amountArg.getClValue(), instanceOf(CLValueU512.class));
        assertThat(amountArg.getClValue().getValue(), is(BigInteger.valueOf(amount)));
    }

    @And("the deploy has a valid hash")
    public void theDeployHasAValidHash() {
        assertThat(getDeployData().getDeploy().getHash().isValid(), is(true));

        // Obtain the hash of the put deploy and compare to one obtained with info_get_deploy
        final DeployResult deployResult = parameterMap.get("deployResult");
        assertThat(getDeployData().getDeploy().getHash().toString(), is(deployResult.getDeployHash()));
    }

    @And("the deploy has a valid timestamp")
    public void theDeployHasAValidTimestamp() {
        Date timestamp = parameterMap.get("deploy-timestamp");
        assertThat(getDeployData().getDeploy().getHeader().getTimeStamp(), is(timestamp));
    }

    private static DeployData getDeployData() {
        return parameterMap.get("info_get_deploy");
    }

    private NamedArg<?> getNamedArg(final List<NamedArg<?>> namedArgs, final String argName) {
        final Optional<NamedArg<?>> optionalNamedArg = namedArgs.stream()
                .filter(namedArg -> namedArg.getType().equals(argName))
                .findFirst();
        assertThat(optionalNamedArg.isPresent(), is(true));
        return optionalNamedArg.get();
    }
}
