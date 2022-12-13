/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.test.acceptance;

import com.google.common.io.Resources;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class DoppelgangerDetectorAcceptanceTest extends AcceptanceTestBase {

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldDetectDoppelgangerAndAlert() throws Exception {
    final String networkName = "less-swift";
    final BesuNode eth1Node =
        createBesuNode(
            config ->
                config
                    .withMiningEnabled(true)
                    .withMergeSupport(true)
                    .withGenesisFile("besu/preMergeGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 8);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(validatorKeystores).generate();

    final String defaultFeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withDepositsFrom(eth1Node)
                    .withBellatrixEpoch(UInt64.ONE)
                    .withTotalTerminalDifficulty(10001)
                    .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                    .withExecutionEngine(eth1Node)
                    .withValidatorLivenessTracking()
                    .withInitialState(genesis)
                    .withJwtSecretFile(JWT_FILE));

    final TekuValidatorNode firstValidatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withValidatorApiEnabled()
                    .withProposerDefaultFeeRecipient(defaultFeeRecipient)
                    .withInteropModeDisabled()
                    .withDoppelgangerDetectionEnabled()
                    .withBeaconNode(beaconNode));
    final ValidatorKeysApi api = firstValidatorClient.getValidatorKeysApi();

    beaconNode.start();
    firstValidatorClient.start();

    api.assertLocalValidatorListing(Collections.emptyList());

    api.addLocalValidatorsAndExpect(validatorKeystores, "imported");
    firstValidatorClient.waitForLogMessageContaining("No validators doppelganger detected");

    beaconNode.waitForEpochAtOrAbove(3);

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withValidatorApiEnabled()
                    .withProposerDefaultFeeRecipient(defaultFeeRecipient)
                    .withInteropModeDisabled()
                    .withDoppelgangerDetectionEnabled()
                    .withBeaconNode(beaconNode));

    final ValidatorKeysApi secondApi = secondValidatorClient.getValidatorKeysApi();

    secondValidatorClient.start();

    secondApi.assertLocalValidatorListing(Collections.emptyList());

    secondApi.addLocalValidatorsAndExpect(validatorKeystores, "imported");

    secondValidatorClient.waitForLogMessageContaining("Validator doppelganger detected...");
    secondValidatorClient.waitForLogMessageContaining("Detected 8 validators doppelganger");

    firstValidatorClient.stop();
    secondValidatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }
}
