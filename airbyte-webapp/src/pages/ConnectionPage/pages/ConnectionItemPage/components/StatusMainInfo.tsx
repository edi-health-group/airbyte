import { faArrowRight } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import React from "react";
import { Link as ReactLink } from "react-router-dom";
import styled from "styled-components";

import ConnectorCard from "components/ConnectorCard";

import FrequencyConfig from "config/FrequencyConfig.json";
import { Connection, ConnectionStatus } from "core/domain/connection";
import { Source, Destination } from "core/domain/connector/types";
import { FeatureItem, useFeatureService } from "hooks/services/Feature";
import { RoutePaths } from "pages/routePaths";
import { useDestinationDefinitionList } from "services/connector/DestinationDefinitionService";
import { useSourceDefinitionList } from "services/connector/SourceDefinitionService";
import { equal } from "utils/objects";

import EnabledControl from "./EnabledControl";

const MainContainer = styled.div`
  width: 650px;
  display: flex;
  justify-content: space-between;
  padding: 10px 20px;
  background-color: white;
  border-radius: 10px;
  align-items: center;
`;

const ConnectorsLink = styled(ReactLink)`
  cursor: pointer;
  text-decoration: none;
  border-radius: 10px;

  &:hover {
    background-color: ${({ theme }) => theme.greyColor10};
  }
`;

interface StatusMainInfoProps {
  connection: Connection;
  source: Source;
  destination: Destination;
}

export const StatusMainInfo: React.FC<StatusMainInfoProps> = ({ connection, source, destination }) => {
  const { sourceDefinitions } = useSourceDefinitionList();
  const { destinationDefinitions } = useDestinationDefinitionList();
  const { hasFeature } = useFeatureService();

  const sourceDefinition = sourceDefinitions.find(
    (definition) => definition.sourceDefinitionId === source.sourceDefinitionId
  );

  const destinationDefinition = destinationDefinitions.find(
    (definition) => definition.destinationDefinitionId === destination.destinationDefinitionId
  );

  const allowSync = hasFeature(FeatureItem.AllowSync);
  const frequency = FrequencyConfig.find((item) => equal(item.config, connection.schedule));

  const sourceConnectionPath = `../../${RoutePaths.Source}/${source.sourceId}`;
  const destinationConnectionPath = `../../${RoutePaths.Destination}/${destination.destinationId}`;

  return (
    <MainContainer>
      <ConnectorsLink to={sourceConnectionPath}>
        <ConnectorCard
          connectionName={source.sourceName}
          icon={sourceDefinition?.icon}
          connectorName={source.name}
          releaseStage={sourceDefinition?.releaseStage}
        />
      </ConnectorsLink>
      <FontAwesomeIcon icon={faArrowRight} />
      <ConnectorsLink to={destinationConnectionPath}>
        <ConnectorCard
          connectionName={destination.destinationName}
          icon={destinationDefinition?.icon}
          connectorName={destination.name}
          releaseStage={destinationDefinition?.releaseStage}
        />
      </ConnectorsLink>
      {connection.status !== ConnectionStatus.DEPRECATED && (
        <EnabledControl disabled={!allowSync} connection={connection} frequencyText={frequency?.text} />
      )}
    </MainContainer>
  );
};
