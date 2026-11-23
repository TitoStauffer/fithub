import {
    confirmPhoto,
    putToPresignedUrl,
    recordOnlineAssessment,
    requestPhotoUpload,
    type Assessment,
    type AssessmentRequest,
    type Photo,
    type PhotoPose,
    type PhotoUpload,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que a avaliação online precisa da API; injetável nos testes. */
export interface AssessmentApi {
    record(body: AssessmentRequest): Promise<Assessment>;
    requestUpload(assessmentId: string, pose: PhotoPose, contentType: string): Promise<PhotoUpload>;
    /** Lê o arquivo local e envia para a URL pré-assinada. */
    upload(uploadUrl: string, fileUri: string, contentType: string): Promise<void>;
    confirm(assessmentId: string, pose: PhotoPose, uploadId: string): Promise<Photo>;
}

export function assessmentApiFrom(apiFetch: ApiFetch, fetchImpl: typeof fetch = fetch): AssessmentApi {
    return {
        record: (body) => recordOnlineAssessment(apiFetch, body),
        requestUpload: (assessmentId, pose, contentType) =>
            requestPhotoUpload(apiFetch, assessmentId, pose, contentType),
        upload: async (uploadUrl, fileUri, contentType) => {
            // O arquivo local vira Blob pelo próprio fetch do React Native, sem passar pela rede.
            const file = await (await fetchImpl(fileUri)).blob();
            await putToPresignedUrl(uploadUrl, file, contentType, fetchImpl);
        },
        confirm: (assessmentId, pose, uploadId) => confirmPhoto(apiFetch, assessmentId, pose, uploadId),
    };
}

/** Foto tirada agora, ainda só no cache do app. */
export interface Shot {
    uri: string;
    contentType: string;
}

/**
 * De onde vêm as fotos (câmera do aparelho); injetável nos testes. `discard` apaga o arquivo local: a foto não
 * fica no aparelho depois do envio, com ou sem sucesso (DEV-0050).
 */
export interface PhotoSource {
    take(): Promise<Shot | null>;
    discard(uri: string): Promise<void>;
}
