import React, { useMemo, useState } from "react";
import { FormattedMessage } from "react-intl";

import ContentCard from "components/ContentCard";
import ServiceForm from "components/ServiceForm";
import { AnalyticsService } from "core/analytics/AnalyticsService";
import config from "config";
import useRouter from "components/hooks/useRouterHook";
import { useSourceDefinitionSpecificationLoad } from "components/hooks/services/useSourceHook";
import { JobInfo } from "core/resources/Scheduler";
import { JobsLogItem } from "components/JobItem";
import { createFormErrorMessage } from "utils/errorStatusMessage";
import { ConnectionConfiguration } from "core/domain/connection";
import { SourceDefinition } from "core/resources/SourceDefinition";

type IProps = {
  onSubmit: (values: {
    name: string;
    serviceType: string;
    sourceDefinitionId?: string;
    connectionConfiguration?: ConnectionConfiguration;
  }) => void;
  afterSelectConnector?: () => void;
  sourceDefinitions: SourceDefinition[];
  hasSuccess?: boolean;
  error?: { message?: string; status?: number } | null;
  jobInfo?: JobInfo;
};

const SourceForm: React.FC<IProps> = ({
  onSubmit,
  sourceDefinitions,
  error,
  hasSuccess,
  jobInfo,
  afterSelectConnector,
}) => {
  const { location } = useRouter();

  const availableServices = useMemo(
    () =>
      sourceDefinitions.map((item) => ({
        text: item.name,
        value: item.sourceDefinitionId,
        icon: item.icon,
      })),
    [sourceDefinitions]
  );

  const [sourceDefinitionId, setSourceDefinitionId] = useState(
    location.state?.sourceDefinitionId || ""
  );
  const {
    sourceDefinitionSpecification,
    isLoading,
  } = useSourceDefinitionSpecificationLoad(sourceDefinitionId);
  const onDropDownSelect = (sourceDefinitionId: string) => {
    setSourceDefinitionId(sourceDefinitionId);
    const connector = availableServices.find(
      (item) => item.value === sourceDefinitionId
    );

    if (afterSelectConnector) {
      afterSelectConnector();
    }

    AnalyticsService.track("New Source - Action", {
      user_id: config.ui.workspaceId,
      action: "Select a connector",
      connector_source_definition: connector?.text,
      connector_source_definition_id: sourceDefinitionId,
    });
  };

  const onSubmitForm = async (values: {
    name: string;
    serviceType: string;
  }) => {
    await onSubmit({
      ...values,
      sourceDefinitionId: sourceDefinitionSpecification?.sourceDefinitionId,
    });
  };

  const errorMessage = error ? createFormErrorMessage(error) : null;

  return (
    <ContentCard title={<FormattedMessage id="onboarding.sourceSetUp" />}>
      <ServiceForm
        onDropDownSelect={onDropDownSelect}
        onSubmit={onSubmitForm}
        formType="source"
        availableServices={availableServices}
        specifications={sourceDefinitionSpecification?.connectionSpecification}
        hasSuccess={hasSuccess}
        errorMessage={errorMessage}
        isLoading={isLoading}
        formValues={
          sourceDefinitionId
            ? { serviceType: sourceDefinitionId, name: "" }
            : undefined
        }
        allowChangeConnector
      />
      <JobsLogItem jobInfo={jobInfo} />
    </ContentCard>
  );
};

export default SourceForm;
