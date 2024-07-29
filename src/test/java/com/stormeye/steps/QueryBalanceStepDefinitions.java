package com.stormeye.steps;

import com.casper.sdk.helper.CasperTransferHelper;
import com.casper.sdk.identifier.block.HashBlockIdentifier;
import com.casper.sdk.identifier.global.BlockHashIdentifier;
import com.casper.sdk.identifier.global.StateRootHashIdentifier;
import com.casper.sdk.identifier.purse.MainPurseUnderAccountHash;
import com.casper.sdk.identifier.purse.MainPurseUnderPublickey;
import com.casper.sdk.identifier.purse.PurseIdentifier;
import com.casper.sdk.identifier.purse.PurseUref;
import com.casper.sdk.model.account.PublicKeyIdentifier;
import com.casper.sdk.model.balance.QueryBalanceData;
import com.casper.sdk.model.block.BlockWithSignatures;
import com.casper.sdk.model.common.Ttl;
import com.casper.sdk.model.deploy.Deploy;
import com.casper.sdk.model.deploy.DeployData;
import com.casper.sdk.model.deploy.DeployResult;
import com.casper.sdk.model.entity.AddressableEntity;
import com.casper.sdk.model.entity.StateEntityResult;
import com.casper.sdk.model.key.PublicKey;
import com.casper.sdk.model.stateroothash.StateRootHashData;
import com.casper.sdk.model.transaction.execution.ExecutionResultV2;
import com.casper.sdk.model.uref.URef;
import com.casper.sdk.service.CasperService;
import com.fasterxml.jackson.databind.JsonNode;
import com.stormeye.utils.CasperClientProvider;
import com.stormeye.utils.DeployUtils;
import com.stormeye.utils.SimpleRcpClient;
import com.stormeye.utils.TestProperties;
import com.syntifi.crypto.key.AbstractPrivateKey;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import static com.stormeye.utils.AssetUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Step definitions for the query_balance.feature
 */
public class QueryBalanceStepDefinitions {

    private final CasperService casperService = CasperClientProvider.getInstance().getCasperService();
    private final TestProperties testProperties = new TestProperties();
    private final SimpleRcpClient simpleRcpClient = new SimpleRcpClient(
            testProperties.getHostname(), testProperties.getRcpPort()
    );
    private QueryBalanceData queryBalanceData;
    private JsonNode queryBalanceJson;
    private DeployData deployData;
    private long transferAmount;
    private BlockWithSignatures initialBlock;
    private QueryBalanceData initialBalance;
    private String initialStateRootHash;

    @Given("that a query balance is obtained by main purse public key")
    public void thatAQueryBalanceIsObtainedByMainPursePublicKey() throws Exception {
        final PublicKey publicKey = getFaucetPublicKey();

        final PurseIdentifier purseIdentifier = MainPurseUnderPublickey.builder()
                .publicKey(publicKey)
                .build();

        queryBalanceData = casperService.queryBalance(null, purseIdentifier);
        queryBalanceJson = simpleRcpClient.queryBalance("main_purse_under_public_key", publicKey.getAlgoTaggedHex());
    }

    @Then("a valid query_balance_result is returned")
    public void aValidQuery_balance_resultIsReturned() {
        assertThat(queryBalanceData, is(notNullValue()));
        assertThat(queryBalanceJson, is(notNullValue()));
    }

    @And("the query_balance_result has an API version of {string}")
    public void theQuery_balance_resultHasAnAPIVersionOf(final String apiVersion) {
        assertThat(queryBalanceData.getApiVersion(), is(apiVersion));
    }

    @And("the query_balance_result has a valid balance")
    public void theQuery_balance_resultHasAValidBalance() {
        final BigInteger expected = new BigInteger(queryBalanceJson.at("/result/balance").asText());
        assertThat(queryBalanceData.getBalance(), is(expected));
    }

    @Given("that a query balance is obtained by main purse account hash")
    public void thatAQueryBalanceIsObtainedByMainPurseAccountHash() throws Exception {
        final PublicKey publicKey = getFaucetPublicKey();
        final PurseIdentifier purseIdentifier = MainPurseUnderAccountHash.builder()
                .accountHash(publicKey.generateAccountHash(true))
                .build();

        queryBalanceData = casperService.queryBalance(null, purseIdentifier);
        queryBalanceJson = simpleRcpClient.queryBalance(
                "main_purse_under_account_hash",
                publicKey.generateAccountHash(true)
        );
    }

    @Given("that a query balance is obtained by main purse uref")
    public void thatAQueryBalanceIsObtainedByMainPurseUref() throws Exception {

        final PublicKey publicKey = getFaucetPublicKey();
        final HashBlockIdentifier identifier = new HashBlockIdentifier(casperService.getBlock().getBlockWithSignatures().getBlock().getHash().toString());
        final StateEntityResult stateEntity = casperService.getStateEntity(new PublicKeyIdentifier(publicKey), identifier);
        final URef mainPurse = ((AddressableEntity) stateEntity.getEntity()).getEntity().getMainPurse();

        final PurseIdentifier purseIdentifier = PurseUref.builder()
                .purseURef(mainPurse)
                .build();

        queryBalanceData = casperService.queryBalance(null, purseIdentifier);
        queryBalanceJson = simpleRcpClient.queryBalance("purse_uref", mainPurse.getJsonURef());
    }

    @When("a transfer of {long} is made to user-{int}'s purse")
    public void aTransferOfIsMadeToUserPurse(long amount, int userId) throws Exception {

        this.transferAmount = amount;
        initialBlock = casperService.getBlock().getBlockWithSignatures();
        this.initialStateRootHash = casperService.getStateRootHash().getStateRootHash();

        final AbstractPrivateKey faucetPrivateKey = getFaucetPrivateKey();

        final PublicKey userPublicKey = getUserPublicKey(userId);

        initialBalance = casperService.queryBalance(
                null,
                MainPurseUnderPublickey.builder().publicKey(userPublicKey).build()
        );

        final Deploy deploy = CasperTransferHelper.buildTransferDeploy(
                faucetPrivateKey,
                userPublicKey,
                BigInteger.valueOf(amount),
                testProperties.getChainName(),
                Math.abs(new Random().nextLong()),
                BigInteger.valueOf(100000000L),
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>());

        final DeployResult deployResult = casperService.putDeploy(deploy);
        deployData = DeployUtils.waitForDeploy(deployResult.getDeployHash(), 300, casperService);


        // Assert successful transfer
        assertThat(deployData.getExecutionInfo().getExecutionResult(), is(notNullValue()));
        assertThat(((ExecutionResultV2) deployData.getExecutionInfo().getExecutionResult()).getEffects().size(), is(greaterThan(0)));
        assertThat(deployData.getExecutionInfo().getBlockHash(), is(not(initialBlock.getBlock().getHash().toString())));
    }

    @And("that a query balance is obtained by user-{int}'s main purse public and latest block identifier")
    public void thatAQueryBalanceIsObtainedByMainPursePublicKeyOfUserAndLatestBlockIdentifier(int userId) throws Exception {
        final PublicKey publicKey = getUserPublicKey(userId);
        final PurseIdentifier purseIdentifier = MainPurseUnderPublickey.builder().publicKey(publicKey).build();

        // obtain using block updated in transfer
        queryBalanceData = casperService.queryBalance(
                BlockHashIdentifier.builder().hash(deployData.getExecutionInfo().getBlockHash().toString()).build(),

                purseIdentifier
        );

    }

    @Then("the balance includes the transferred amount")
    public void theBalanceIncludesTheTransferredAmount() {
        assertThat(queryBalanceData.getBalance().longValue(), is(initialBalance.getBalance().longValue() + transferAmount));
    }

    @When("that a query balance is obtained by user-{int}'s main purse public key and previous block identifier")
    public void thatAQueryBalanceIsObtainedByMainPursePublicKeyOfUserAndPreviousBlockIdentifier(int userId) throws Exception {
        final PublicKey publicKey = getUserPublicKey(userId);

        final PurseIdentifier purseIdentifier = MainPurseUnderPublickey.builder()
                .publicKey(publicKey)
                .build();

        // obtain using initial block before transfer
        queryBalanceData = casperService.queryBalance(
                BlockHashIdentifier.builder().hash(initialBlock.getBlock().getHash().toString()).build(),
                purseIdentifier
        );
    }

    @And("that a query balance is obtained by user-{int}'s main purse public and latest state root hash identifier")
    public void thatAQueryBalanceIsObtainedByUserSMainPursePublicAndLatestStateRootHashIdentifier(int userId) throws Exception {
        final PublicKey publicKey = getUserPublicKey(userId);

        final PurseIdentifier purseIdentifier = MainPurseUnderPublickey.builder()
                .publicKey(publicKey)
                .build();

        final StateRootHashData stateRootHash = casperService.getStateRootHash();
        assertThat(stateRootHash.getStateRootHash(), is(not(initialStateRootHash)));

        // obtain using initial block before transfer
        queryBalanceData = casperService.queryBalance(
                StateRootHashIdentifier.builder().hash(stateRootHash.getStateRootHash()).build(),
                purseIdentifier
        );
    }

    @When("that a query balance is obtained by user-{int}'s main purse public key and previous state root hash identifier")
    public void thatAQueryBalanceIsObtainedByUserSMainPursePublicKeyAndPreviousStateRootHashIdentifier(int userId) throws Exception {
        final PublicKey publicKey = getUserPublicKey(userId);

        final PurseIdentifier purseIdentifier = MainPurseUnderPublickey.builder()
                .publicKey(publicKey)
                .build();

        // obtain using initial block before transfer
        queryBalanceData = casperService.queryBalance(
                StateRootHashIdentifier.builder().hash(initialStateRootHash).build(),
                purseIdentifier
        );
    }

    @Then("the balance is the pre transfer amount")
    public void theBalanceIsThePreTransferAmount() {
        assertThat(queryBalanceData.getBalance().longValue(), is(initialBalance.getBalance().longValue()));
    }
}
