package com.stormeye.steps;

import com.casper.sdk.model.clvalue.CLValueAny;
import com.casper.sdk.model.clvalue.CLValueMap;
import com.casper.sdk.model.clvalue.cltype.CLTypeMap;
import com.casper.sdk.model.deploy.Deploy;
import com.casper.sdk.model.deploy.DeployData;
import com.casper.sdk.model.deploy.DeployResult;
import com.casper.sdk.model.deploy.NamedArg;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stormeye.utils.AssetUtils;
import com.stormeye.utils.CasperClientProvider;
import com.stormeye.utils.DeployUtils;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import static com.stormeye.utils.DeployUtils.buildStandardTransferDeploy;
import static com.stormeye.utils.DeployUtils.getNamedArgValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;


/**
 * The Any Value feature step definitions.
 *
 * @author ian@meywood.com
 */
public class AnyValueStepDefinitions {
    private CLValueAny clValueAny;
    private DeployResult deployResult;
    private DeployData deployData;
    private CLValueMap clValueMap;

    @Given("an Any value contains a byte array value of {string}")
    public void iHaveASimpleAnyValueContainingAStringValue(final String strValue) throws Throwable {
        clValueAny = new CLValueAny(Hex.decode(strValue));
    }

    @Then("the any value's bytes are {string}")
    public void theAnyValueSBytesAre(String hexBytes) {
        assertThat(clValueAny.getBytes(), is(hexBytes));
    }

    @Given("that the any value is deployed in a transfer as a named argument")
    public void thatTheAnyValueIsDeployedInATransferAsANamedArgument() throws Exception {
        final List<NamedArg<?>> transferArgs = new LinkedList<>();
        transferArgs.add(new NamedArg<>("ANY", clValueAny));

        final Deploy deploy = buildStandardTransferDeploy(transferArgs);

        deployResult = CasperClientProvider.getInstance().getCasperService().putDeploy(deploy);

        clValueAny = null;
    }

    @And("the transfer containing the any value is successfully executed")
    public void theTransferContainingTheAnyValueIsSuccessfullyExecuted() {
        deployData = DeployUtils.waitForDeploy(
                deployResult.getDeployHash(),
                300,
                CasperClientProvider.getInstance().getCasperService()
        );
    }

    @When("the any is read from the deploy")
    public void theAnyIsReadFromTheDeploy() {
        clValueAny = (CLValueAny) getNamedArgValue(deployData.getDeploy().getSession().getArgs(), "ANY");
        assertThat(clValueAny, is(notNullValue()));
    }

    @Given("that the map of public keys to any types is read from resource {string}")
    public void thatTheMapOfPublicKeysToAnyTypesIsReadFromResource(final String jsonResource) throws IOException {
        final URL jsonUrl = AssetUtils.getStandardTestResourceURL("/json/" + jsonResource);
        clValueMap = new ObjectMapper().readValue(jsonUrl, CLValueMap.class);
    }

    @Then("the loaded CLMap will contain {int} elements as nested any values are not supported")
    public void theLoadedCLMapWillContainElements(int count) {
        assertThat(clValueMap.getValue(), is(nullValue()));
    }

    @And("the nested map key type will be {string}")
    public void theMapKeyTypeWillBe(final String type) {
        CLTypeMap innerTypes = (CLTypeMap) this.clValueMap.getClType().getChildTypes().get(1);
        assertThat(innerTypes.getChildTypes().get(0).getTypeName(), is(type));
    }

    @And("the nested map value type will be {string}")
    public void theMapValueTypeWillBe(final String type) {
        CLTypeMap innerTypes = (CLTypeMap) this.clValueMap.getClType().getChildTypes().get(1);
        assertThat(innerTypes.getChildTypes().get(1).getTypeName(), is(type));
    }

    @And("the maps bytes will be {string}")
    public void theMapsBytesWillBe(final String hexBytes) {
        assertThat(clValueMap.getBytes(), is(hexBytes));
    }
}
