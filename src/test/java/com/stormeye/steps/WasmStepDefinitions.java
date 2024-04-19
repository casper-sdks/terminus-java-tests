package com.stormeye.steps;

import com.casper.sdk.exception.NoSuchTypeException;
import com.casper.sdk.helper.CasperConstants;
import com.casper.sdk.helper.CasperDeployHelper;
import com.casper.sdk.identifier.dictionary.*;
import com.casper.sdk.model.account.Account;
import com.casper.sdk.model.clvalue.*;
import com.casper.sdk.model.common.Ttl;
import com.casper.sdk.model.contract.NamedKey;
import com.casper.sdk.model.deploy.Deploy;
import com.casper.sdk.model.deploy.DeployData;
import com.casper.sdk.model.deploy.DeployResult;
import com.casper.sdk.model.deploy.NamedArg;
import com.casper.sdk.model.deploy.executabledeploy.*;
import com.casper.sdk.model.deploy.executionresult.Success;
import com.casper.sdk.model.dictionary.DictionaryData;
import com.casper.sdk.model.key.PublicKey;
import com.casper.sdk.model.stateroothash.StateRootHashData;
import com.casper.sdk.model.storedvalue.StoredValueAccount;
import com.casper.sdk.model.storedvalue.StoredValueData;
import com.casper.sdk.model.uref.URef;
import com.casper.sdk.service.CasperService;
import com.stormeye.utils.*;
import com.syntifi.crypto.key.Ed25519PrivateKey;
import com.syntifi.crypto.key.encdec.Hex;
import dev.oak3.sbs4j.exception.ValueSerializationException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.cxf.helpers.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.*;

import static com.casper.sdk.helper.CasperDeployHelper.getPaymentModuleBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Step definitions for smart contracts
 *
 * @author ian@meywood.com
 */
public class WasmStepDefinitions {

    private final ContextMap contextMap = ContextMap.getInstance();
    private final Logger logger = LoggerFactory.getLogger(StateGetDictionaryItemStepDefinitions.class);
    public final CasperService casperService = CasperClientProvider.getInstance().getCasperService();

    private final TestProperties testProperties = new TestProperties();

    @Given("that a smart contract {string} is located in the {string} folder")
    public void thatASmartContractIsInTheFolder(String wasmFileName, String contractsFolder) throws IOException {
        logger.info("Give that a smart contract {string} is in the {string} folder");

        final URL wasmUrl = AssetUtils.getStandardTestResourceURL("/" + contractsFolder + "/" + wasmFileName);
        contextMap.put(StepConstants.WASM_PATH, wasmUrl);
        assertThat(wasmUrl.openStream(), is(notNullValue()));
    }

    @When("the wasm is loaded as from the file system")
    public void whenTheWasmIsLoadedAsFromTheFileSystem() throws IOException, ValueSerializationException, NoSuchTypeException, GeneralSecurityException {
        logger.info("Then when the wasm is loaded as from the file system");

        final URL resource = contextMap.get(StepConstants.WASM_PATH);

        final byte[] bytes = IOUtils.readBytesFromStream(resource.openStream());
        //assertThat(bytes.length, is(189336));

        final String chainName = testProperties.getChainName();
        final BigInteger payment = new BigInteger("200000000000");
        final byte tokenDecimals = 11;
        final String tokenName = "Acme Token";
        final BigInteger tokenTotalSupply = new BigInteger("500000000000");
        final String tokenSymbol = "ACME";

        // Load faucet private key
        final URL faucetPrivateKeyUrl = AssetUtils.getFaucetAsset(1, "secret_key.pem");
        assertThat(faucetPrivateKeyUrl, is(notNullValue()));
        final Ed25519PrivateKey privateKey = new Ed25519PrivateKey();
        privateKey.readPrivateKey(faucetPrivateKeyUrl.getFile());

        this.contextMap.put("faucetPrivateKey", privateKey);

        final List<NamedArg<?>> paymentArgs = new LinkedList<>();
        //paymentArgs.add(new NamedArg<>("amount", new CLValueU512(payment)));
        paymentArgs.add(new NamedArg<>("token_decimals", new CLValueU8(tokenDecimals)));
        paymentArgs.add(new NamedArg<>("token_name", new CLValueString(tokenName)));
        paymentArgs.add(new NamedArg<>("token_symbol", new CLValueString(tokenSymbol)));
        paymentArgs.add(new NamedArg<>("token_total_supply", new CLValueU256(tokenTotalSupply)));


        final ModuleBytes session = ModuleBytes.builder().bytes(bytes).args(paymentArgs).build();
        final ModuleBytes paymentModuleBytes = getPaymentModuleBytes(payment);

        final Deploy deploy = CasperDeployHelper.buildDeploy(
                privateKey,
                chainName,
                session,
                paymentModuleBytes,
                CasperConstants.DEFAULT_GAS_PRICE.value,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>()
        );

        final DeployResult deployResult = casperService.putDeploy(deploy);
        assertThat(deployResult, is(notNullValue()));
        assertThat(deployResult.getDeployHash(), is(notNullValue()));
        contextMap.put(StepConstants.DEPLOY_RESULT, deployResult);
    }

    @And("the wasm has been successfully deployed")
    public void theWasmHasBeenSuccessfullyDeployed() {

        logger.info("the wasm has been successfully deployed");

        final DeployResult deployResult = contextMap.get(StepConstants.DEPLOY_RESULT);

        logger.info("the Deploy {} is accepted", deployResult.getDeployHash());

        final DeployData deployData = DeployUtils.waitForDeploy(deployResult.getDeployHash(), 300, casperService);

        assertThat(deployData, is(notNullValue()));
        assertThat(deployData.getDeploy(), is(notNullValue()));
        assertThat(deployData.getExecutionResults(), is(not(empty())));
        assertThat(deployData.getExecutionResults().get(0).getResult(), is(instanceOf(Success.class)));
    }

    @Then("the account named keys contain the {string} name")
    public void theAccountNamedKeysContainThe(final String contractName) throws Exception {
        validateContractInstalled(contractName, false);
    }


    @Then("the account named keys contain the {string} name and a version uref")
    public void theAccountNamedKeysContainTheNameAndAVersionUref(final String contractName) throws Exception {
        validateContractInstalled(contractName, true);
    }

    @And("the contract data {string} is a {string} with a value of {string} and bytes of {string}")
    public void theContractDataIsAWithAValueOf(final String path,
                                               final String typeName,
                                               final String value,
                                               final String hexBytes) {

        final String stateRootHash = this.contextMap.get("stateRootHash");
        final String contractHash = this.contextMap.get("contractHash");

        //noinspection deprecation
        final StoredValueData stateItem = this.casperService.getStateItem(
                stateRootHash,
                contractHash,
                Collections.singletonList(path)
        );

        //noinspection rawtypes
        final AbstractCLValue clValue = (AbstractCLValue) stateItem.getStoredValue().getValue();
        assertThat(clValue.getClType().getTypeName(), is(typeName));

        final Object expectedValue = CLTypeUtils.convertToCLTypeValue(typeName, value);
        assertThat(clValue.getValue(), is(expectedValue));

        assertThat(clValue.getBytes(), is(hexBytes));
    }

    @And("the contract dictionary item {string} is a {string} with a value of {string} and bytes of {string}")
    public void theContractDictionaryItemIsAWithAValueOfAndBytesOf(final String dictionary,
                                                                   final String typeName,
                                                                   final String value,
                                                                   final String hexBytes) throws ValueSerializationException {

        final String stateRootHash = this.casperService.getStateRootHash().getStateRootHash();
        final String contractHash = this.contextMap.get("contractHash");
        final Ed25519PrivateKey faucetPrivateKey = this.contextMap.get("faucetPrivateKey");

        final CLValuePublicKey clValuePublicKey = new CLValuePublicKey(PublicKey.fromAbstractPublicKey(faucetPrivateKey.derivePublicKey()));
        final byte[] decode = Hex.decode(clValuePublicKey.getBytes());
        final byte[] encode = Base64.getEncoder().encode(decode);
        final String balanceKey = Hex.encode(encode);

        final ContractNamedKeyDictionaryIdentifier identifier = ContractNamedKeyDictionaryIdentifier
                .builder()
                .contractNamedKey(ContractNamedKey.builder().dictionaryItemKey(contractHash).dictionaryName(dictionary).key(balanceKey).build())
                .build();
        final DictionaryData stateDictionaryItem = this.casperService.getStateDictionaryItem(
                stateRootHash,
                identifier
        );

        final AbstractCLValue<?, ?> clValue = (AbstractCLValue<?, ?>) stateDictionaryItem.getStoredValue().getValue();
        assertThat(clValue.getClType().getTypeName(), is(typeName));

        final Object expectedValue = CLTypeUtils.convertToCLTypeValue(typeName, value);
        assertThat(clValue.getValue(), is(expectedValue));
        assertThat(clValue.getBytes(), is(hexBytes));
    }


    @When("the contract entry point is invoked by hash with a transfer amount of {string}")
    public void theContractEntryPointIsInvokedWithATransferAmountOf(final String transferAmount) throws Exception {

        // Create new recipient
        final Ed25519PrivateKey recipientPrivateKey = Ed25519PrivateKey.deriveRandomKey();
        final PublicKey recipient = PublicKey.fromAbstractPublicKey(recipientPrivateKey.derivePublicKey());
        final BigInteger amount = new BigInteger(transferAmount);
        final String contractHash = ((String) this.contextMap.get("contractHash")).substring(5);
        final Ed25519PrivateKey faucetPrivateKey = this.contextMap.get("faucetPrivateKey");
        final String accountHash = recipient.generateAccountHash(false);

        final List<NamedArg<?>> args = Arrays.asList(
                new NamedArg<>("amount", new CLValueU256(amount))
        );

        final StoredContractByHash session = StoredContractByHash.builder()
                .entryPoint("transfer")
                .hash(contractHash)
                .args(args)
                .build();

        final ModuleBytes payment = getPaymentModuleBytes(new BigInteger("2500000000"));

        final String chainName = testProperties.getChainName();
        final Deploy transferDeploy = CasperDeployHelper.buildDeploy(faucetPrivateKey,
                chainName,
                session,
                payment,
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>()
        );

        final DeployResult deployResult = this.casperService.putDeploy(transferDeploy);

        assertThat(deployResult.getDeployHash(), is(notNullValue()));

        this.contextMap.put("transferDeploy", transferDeploy);
    }

    @Then("the contract invocation deploy is successful")
    public void theContractInvocationDeployIsSuccessful() {


        final Deploy transferDeploy = this.contextMap.get("transferDeploy");
        final DeployData deployData = DeployUtils.waitForDeploy(transferDeploy.getHash().toString(), 300, casperService);

        assertThat(deployData, is(notNullValue()));
        assertThat(deployData.getDeploy(), is(notNullValue()));
        assertThat(deployData.getExecutionResults(), is(not(empty())));
        assertThat(deployData.getExecutionResults().get(0).getResult(), is(instanceOf(Success.class)));
    }

    @When("the the contract is invoked by name {string} and a transfer amount of {string}")
    public void theTheContractIsInvokedByNameAndATransferAmountOf(final String contractName,
                                                                  final String transferAmount) throws Exception {

        final Ed25519PrivateKey recipientPrivateKey = Ed25519PrivateKey.deriveRandomKey();
        final PublicKey recipient = PublicKey.fromAbstractPublicKey(recipientPrivateKey.derivePublicKey());
        final BigInteger amount = new BigInteger(transferAmount);
        final Ed25519PrivateKey faucetPrivateKey = this.contextMap.get("faucetPrivateKey");
        final String accountHash = recipient.generateAccountHash(false);

        final List<NamedArg<?>> args = Arrays.asList(
                new NamedArg<>("recipient", new CLValueByteArray(Hex.decode(accountHash))),
                new NamedArg<>("amount", new CLValueU256(amount))
        );

        final StoredContractByName session = StoredContractByName.builder()
                .name(contractName.toUpperCase())
                .entryPoint("counter_inc")
                .args(args)
                .build();

        final ModuleBytes payment = getPaymentModuleBytes(new BigInteger("2500000000"));

        final String chainName = testProperties.getChainName();
        final Deploy transferDeploy = CasperDeployHelper.buildDeploy(faucetPrivateKey,
                chainName,
                session,
                payment,
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>()
        );

        final DeployResult deployResult = this.casperService.putDeploy(transferDeploy);

        assertThat(deployResult.getDeployHash(), is(notNullValue()));

        this.contextMap.put("transferDeploy", transferDeploy);
    }

    @When("the the contract is invoked by name {string}, and version {long}, and entry point of {string}, and with a payment amount of {string}")
    public void theTheContractIsInvokedByNameAndVersionWithATransferAmountOf(final String contractName,
                                                                             final long version,
                                                                             final String entryPoint,
                                                                             final String payment) throws Exception {
        final Ed25519PrivateKey recipientPrivateKey = Ed25519PrivateKey.deriveRandomKey();
        final Ed25519PrivateKey faucetPrivateKey = this.contextMap.get("faucetPrivateKey");

        final List<NamedArg<?>> args = Arrays.asList(
                new NamedArg<>("amount", new CLValueU256(new BigInteger(payment)))
        );

        final StoredVersionedContractByName session = StoredVersionedContractByName.builder()
                .name(contractName)
                .version(version)
                .entryPoint(entryPoint)
                .args(args)
                .build();

        final ModuleBytes paymentBytes = getPaymentModuleBytes(new BigInteger(payment));

        final String chainName = testProperties.getChainName();
        final Deploy transferDeploy = CasperDeployHelper.buildDeploy(faucetPrivateKey,
                chainName,
                session,
                paymentBytes,
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>()
        );

        final DeployResult deployResult = this.casperService.putDeploy(transferDeploy);

        assertThat(deployResult.getDeployHash(), is(notNullValue()));

        this.contextMap.put("transferDeploy", transferDeploy);
    }

    @And("the the contract is invoked by hash, and version {int}, and entry point of {string}, and with a payment amount of {string}")
    public void theTheContractIsInvokedByHashAndVersionWithATransferAmountOf(final long version, final String entryPoint, final String payment) throws
            Exception {
        final String contractHash = ((String) this.contextMap.get("contractHash")).substring(5);
        final Ed25519PrivateKey faucetPrivateKey = this.contextMap.get("faucetPrivateKey");

        final List<NamedArg<?>> args = Arrays.asList(
                new NamedArg<>("amount", new CLValueU256(new BigInteger(payment)))
        );

        final StoredVersionedContractByHash session = StoredVersionedContractByHash.builder()
                .entryPoint(entryPoint)
                .hash(contractHash)
                .version(version)
                .args(args)
                .build();

        final ModuleBytes paymentBytes = getPaymentModuleBytes(new BigInteger(payment));

        final String chainName = testProperties.getChainName();
        final Deploy transferDeploy = CasperDeployHelper.buildDeploy(faucetPrivateKey,
                chainName,
                session,
                paymentBytes,
                1L,
                Ttl.builder().ttl("30m").build(),
                new Date(),
                new ArrayList<>()
        );

        final DeployResult deployResult = this.casperService.putDeploy(transferDeploy);

        assertThat(deployResult.getDeployHash(), is(notNullValue()));

        this.contextMap.put("transferDeploy", transferDeploy);
    }

    private void validateContractInstalled(final String contractName, final boolean obtainVersionUref) throws InterruptedException, IOException {
        Thread.sleep(5000L);

        final Ed25519PrivateKey privateKey = this.contextMap.get("faucetPrivateKey");
        PublicKey publicKey = PublicKey.fromAbstractPublicKey(privateKey.derivePublicKey());
        final String accountHash = publicKey.generateAccountHash(true);
        final StringDictionaryIdentifier key = StringDictionaryIdentifier.builder().dictionary(accountHash).build();

        final StateRootHashData stateRootHash = this.casperService.getStateRootHash();
        this.contextMap.put("stateRootHash", stateRootHash.getStateRootHash());
        //noinspection deprecation
        final StoredValueData stateItem = this.casperService.getStateItem(
                stateRootHash.getStateRootHash(),
                key.getDictionary(),
                new ArrayList<>());

        assertThat(stateItem, is(notNullValue()));
        assertThat(stateItem.getStoredValue(), is(instanceOf(StoredValueAccount.class)));

        this.contextMap.remove("contractHash");
        this.contextMap.remove("versionUref");

        final Account account = (Account) stateItem.getStoredValue().getValue();
        assertThat(account.getAssociatedKeys(), is(not(empty())));
        account.getNamedKeys().forEach((NamedKey namedKey) -> {

            if (namedKey.getName().equalsIgnoreCase(contractName)) {
                if (namedKey.getKey().startsWith("hash")) {
                    this.contextMap.put("contractHash", namedKey.getKey());
                }
            }

            if (namedKey.getName().equalsIgnoreCase("counter_package_name")) {
                this.contextMap.put("counterPackageHash", namedKey.getKey());
            }

            if (namedKey.getName().equalsIgnoreCase("version")) {
                this.contextMap.put("versionUref", namedKey.getKey());
            }
        });

        assertThat(this.contextMap.get("contractHash"), is(notNullValue()));
        assertThat(this.contextMap.get("counterPackageHash"), is(notNullValue()));

        if (obtainVersionUref) {
            assertThat(this.contextMap.get("versionUref"), is(notNullValue()));
        }
    }

    @And("the version uref's dictionary item value is {long}")
    public void theVersionUrefSDictionaryItemValueIs(final long version) throws Exception {

        final URef versionUref = URef.fromString(contextMap.get("versionUref"));
        final String stateRootHash = this.contextMap.get("stateRootHash");

        final URefDictionaryIdentifier dictionaryIdentifier = URefDictionaryIdentifier.builder()
                .uref(URefSeed.builder().dictionaryItemKey("version").uref(versionUref).build())
                .build();

        final DictionaryData dictionaryData = this.casperService.getStateDictionaryItem(stateRootHash, dictionaryIdentifier);

        assertThat(dictionaryData, is(notNullValue()));
    }


}
