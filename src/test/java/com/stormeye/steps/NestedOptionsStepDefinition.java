package com.stormeye.steps;

import com.casper.sdk.model.clvalue.*;
import com.casper.sdk.model.deploy.Deploy;
import com.casper.sdk.model.deploy.DeployData;
import com.casper.sdk.model.deploy.DeployResult;
import com.casper.sdk.model.deploy.NamedArg;
import com.stormeye.utils.CasperClientProvider;
import com.stormeye.utils.DeployUtils;
import com.syntifi.crypto.key.encdec.Hex;
import dev.oak3.sbs4j.exception.ValueSerializationException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.*;

import static com.stormeye.utils.DeployUtils.buildStandardTransferDeploy;
import static com.stormeye.utils.DeployUtils.getNamedArgValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Step definitions for the nested options feature.
 *
 * @author ian@meywood.com
 */
public class NestedOptionsStepDefinition {

    private CLValueOption option;
    private DeployResult deployResult;
    private DeployData deployData;

    @Given("^that a nested Option has an inner type of Option with a type of String and a value of \"([^\"]*)\"$")
    public void thatANestedOptionHasAnInnerTypeOfOptionWithATypeOfStringAndAValueOf(final String strVal) throws Throwable {

        CLValueOption innerOption = new CLValueOption(Optional.of(new CLValueString(strVal)));
        option = new CLValueOption(Optional.of(innerOption));

    }

    @Then("^the inner type is Option with a type of String and a value of \"([^\"]*)\"$")
    public void theInnerTypeIsOptionWithATypeOfStringAndAValueOf(final String strValue) {
        assertThat(option.getValue().isPresent(), is(true));
        CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        assertThat(innerOption.getValue().get().getValue(), is(strValue));
    }

    @And("^the bytes are \"([^\"]*)\"$")
    public void theBytesAre(final String hexBytes) {
        assertThat(option.getBytes(), is(hexBytes));
    }

    @Given("^that the nested Option is deployed in a transfer$")
    public void thatTheNestedOptionIsDeployedInATransfer() throws Exception {
        final List<NamedArg<?>> transferArgs = new LinkedList<>();
        transferArgs.add(new NamedArg<>("OPTION", option));

        final Deploy deploy = buildStandardTransferDeploy(transferArgs);

        deployResult = CasperClientProvider.getInstance().getCasperService().putDeploy(deploy);

        option = null;
    }

    @And("^the transfer containing the nested Option is successfully executed$")
    public void theTransferContainingTheNestedOptionIsSuccessfullyExecuted() {
        deployData = DeployUtils.waitForDeploy(
                deployResult.getDeployHash(), 300,
                CasperClientProvider.getInstance().getCasperService()
        );
    }

    @When("^the nested Option is read from the deploy$")
    public void theOptionIsReadFromTheDeploy() {
        option = (CLValueOption) getNamedArgValue(deployData.getDeploy().getSession().getArgs(), "OPTION");
    }

    @Given("^that a nested Option has an inner type of List with a type of U256 and a value of \\((\\d+), (\\d+), (\\d+)\\)$")
    public void thatANestedOptionHasAnInnerTypeOfListWithATypeOfUAndAValueOf(final long arg1, final long arg2, final long arg3) throws ValueSerializationException {
        final CLValueList innerList = new CLValueList(List.of(new CLValueU256(BigInteger.valueOf(arg1)), new CLValueU256(BigInteger.valueOf(arg2)), new CLValueU256(BigInteger.valueOf(arg3))));
        final CLValueOption innerOption = new CLValueOption(Optional.of(innerList));
        option = new CLValueOption(Optional.of(innerOption));
    }

    @And("^the nested list's length is (\\d+)$")
    public void theNestedListSLengthIs(final int len) {
        assertThat(option.getValue().isPresent(), is(true));
        final CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        assertThat(((CLValueList) innerOption.getValue().get()).getValue().size(), is(len));
    }

    @And("^the nested list's \"([^\"]*)\" item is a CLValue with U256 value of (\\d+)$")
    public void theNestedListSItemIsACLValueWithUValueOf(final String index, final long value) {
        final int i = Integer.parseInt(index.substring(0, 1)) - 1;
        assertThat(option.getValue().isPresent(), is(true));
        final CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        final CLValueU256 clValue = (CLValueU256) ((CLValueList) innerOption.getValue().get()).getValue().get(i);
        assertThat(clValue.getValue(), is(BigInteger.valueOf(value)));
    }

    @Given("^that a nested Option has an inner type of Tuple2 with a type of \"([^\"]*)\" values of \\(\"([^\"]*)\", (\\d+)\\)$")
    public void thatANestedOptionHasAnInnerTypeOfTupleWithATypeOfValuesOf(final String ignoredTypes, final String strVal, final long u32Val) throws Throwable {
        CLValueTuple2 innerTuple = new CLValueTuple2(new Pair<>(new CLValueString(strVal), new CLValueU32(u32Val)));
        CLValueOption innerOption = new CLValueOption(Optional.of(innerTuple));
        option = new CLValueOption(Optional.of(innerOption));
    }

    @Then("^the inner type is Tuple2 with a type of \"([^\"]*)\" and a value of \\(\"([^\"]*)\", (\\d+)\\)$")
    public void theInnerTypeIsTupleWithATypeOfAndAValueOf(final String ignoredTypes, final String strVal, long u32Val) {
        assertThat(option.getValue().isPresent(), is(true));
        CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        CLValueTuple2 innerTuple = (CLValueTuple2) innerOption.getValue().get();
        assertThat(innerTuple.getValue().getValue0().getValue(), is(strVal));
        assertThat(innerTuple.getValue().getValue1().getValue(), is(u32Val));
    }

    @Given("^that a nested Option has an inner type of Map with a type of \"([^\"]*)\" value of \\{\"([^\"]*)\": (\\d+)\\}$")
    public void thatANestedOptionHasAnInnerTypeOfMapWithATypeOfValueOf(final String ignoredTypes, final String key, final long val) throws Throwable {
        final Map<CLValueString, CLValueU32> map = new HashMap<>();
        map.put(new CLValueString(key), new CLValueU32(val));
        final CLValueMap innerMap = new CLValueMap(map);
        final CLValueOption innerOption = new CLValueOption(Optional.of(innerMap));
        option = new CLValueOption(Optional.of(innerOption));
    }

    @Then("^the inner type is Map with a type of \"([^\"]*)\" and a value of \\{\"([^\"]*)\": (\\d+)\\}$")
    public void theInnerTypeIsMapWithATypeOfAndAValueOf(final String ignoredTypes, final String key, final long val) throws Throwable {
        assertThat(option.getValue().isPresent(), is(true));
        CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        CLValueMap innerMap = (CLValueMap) innerOption.getValue().get();
        assertThat(innerMap.getValue().size(), is(1));
        assertThat(innerMap.getValue().get(new CLValueString(key)).getValue(), is(val));
    }

    @Given("^that a nested Option has an inner type of Any with a value of \"([^\"]*)\"$")
    public void thatANestedOptionHasAnInnerTypeOfAnyWithAValueOf(final String anyVal) throws Throwable {
        final CLValueAny clValueAny = new CLValueAny(Hex.decode(anyVal));
        final CLValueOption innerOption = new CLValueOption(Optional.of(clValueAny));
        option = new CLValueOption(Optional.of(innerOption));
    }

    @Then("^the inner type is Any with a value of \"([^\"]*)\"$")
    public void theInnerTypeIsAnyWithAValueOf(final String anyVal) {
        assertThat(option.getValue().isPresent(), is(true));
        final CLValueOption innerOption = (CLValueOption) option.getValue().get();
        assertThat(innerOption.getValue().isPresent(), is(true));
        final CLValueAny clValueAny = (CLValueAny) innerOption.getValue().get();
        assertThat(clValueAny.getValue(), is(Hex.decode(anyVal)));
    }
}
