/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext } from 'react';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { PublisherInfo } from '../../extension-registry-types';
import { UpdateContext } from './publisher-admin';
import { useReinstatePublisherAgreement } from './use-publisher-admin';

export const PublisherReinstateAgreementButton: FunctionComponent<PublisherReinstateAgreementButtonProps> = props => {
    const updateContext = useContext(UpdateContext);
    const { mutate: reinstate, isPending: working } = useReinstatePublisherAgreement();
    const { user } = props.publisherInfo;

    const doReinstate = () => {
        reinstate(
            { provider: user.provider as string, login: user.loginName },
            { onSuccess: () => updateContext.handleUpdate() }
        );
    };

    return (
        <ButtonWithProgress working={working} onClick={doReinstate}>
            Reinstate
        </ButtonWithProgress>
    );
};

export interface PublisherReinstateAgreementButtonProps {
    publisherInfo: PublisherInfo;
}
