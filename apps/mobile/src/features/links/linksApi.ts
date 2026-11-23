import {
    acceptInvitation,
    endLink,
    fetchMyLinks,
    lookupInvitation,
    type AcceptInvitationRequest,
    type AcceptedLink,
    type ClientLink,
    type InvitationPreview,
    type LinkStatus,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que as telas de convite e vínculos precisam da API; injetável nos testes. */
export interface LinksApi {
    lookup(target: { token: string } | { code: string }): Promise<InvitationPreview>;
    accept(body: AcceptInvitationRequest): Promise<AcceptedLink>;
    list(): Promise<ClientLink[]>;
    end(linkId: string): Promise<LinkStatus>;
}

export function linksApiFrom(apiFetch: ApiFetch): LinksApi {
    return {
        lookup: (target) => lookupInvitation(apiFetch, target),
        accept: (body) => acceptInvitation(apiFetch, body),
        list: () => fetchMyLinks(apiFetch),
        end: (linkId) => endLink(apiFetch, linkId),
    };
}

/** Prefixo do link do convite (fithub.invitations.link-base do client-service). */
const INVITATION_PREFIX = 'fithub://convite/';

/** Token do convite num link aberto pelo sistema (`fithub://convite/<token>`), ou `undefined`. */
export function tokenFromUrl(url: string | null | undefined): string | undefined {
    if (!url || !url.startsWith(INVITATION_PREFIX)) return undefined;
    const token = url.slice(INVITATION_PREFIX.length).split(/[?#]/)[0] ?? '';
    return /^[A-Za-z0-9_-]{20,}$/.test(token) ? token : undefined;
}

export const SHARED_DATA_LABELS: Record<string, string> = {
    PROFILE: 'Seu perfil (nome, nascimento, gênero, altura e objetivo)',
    HEALTH: 'Seu perfil de saúde',
    ASSESSMENTS: 'Suas avaliações corporais',
    PHOTOS: 'Suas fotos de evolução',
};

export const PROFESSIONAL_LABELS: Record<string, string> = {
    PERSONAL_TRAINER: 'Personal trainer',
    NUTRITIONIST: 'Nutricionista',
};
