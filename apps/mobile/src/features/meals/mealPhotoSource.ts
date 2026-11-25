/** Foto do prato escolhida agora. */
export interface MealShot {
    uri: string;
    contentType: string;
    /** Tirada pela câmera: o arquivo é do app e sai do aparelho depois do envio. Da galeria, é da pessoa e fica. */
    fromCamera: boolean;
}

/** De onde vem a foto do prato (câmera ou galeria); injetável nos testes. */
export interface MealPhotoSource {
    take(): Promise<MealShot | null>;
    pick(): Promise<MealShot | null>;
    /** Apaga do aparelho a foto tirada pela câmera para o envio (DEV-0050). */
    discard(uri: string): Promise<void>;
}

type PickerResult = {
    canceled: boolean;
    assets?: { uri: string; mimeType?: string | null }[] | null;
};

function shotOf(result: PickerResult, fromCamera: boolean): MealShot | null {
    const asset = result.canceled ? undefined : result.assets?.[0];
    if (!asset) return null;
    return {
        uri: asset.uri,
        contentType: asset.mimeType === 'image/png' ? 'image/png' : 'image/jpeg',
        fromCamera,
    };
}

/**
 * Câmera e galeria pelo expo-image-picker, que o app já usa na avaliação (DEV-0050). Os módulos nativos são carregados
 * só no uso, para as telas que não tiram foto (e os testes delas) não dependerem deles.
 */
export const expoMealPhotoSource: MealPhotoSource = {
    async take() {
        const ImagePicker = await import('expo-image-picker');
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) return null;
        return shotOf(
            await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.7, exif: false }),
            true,
        );
    },
    async pick() {
        const ImagePicker = await import('expo-image-picker');
        return shotOf(
            await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.7, exif: false }),
            false,
        );
    },
    async discard(uri) {
        const { File } = await import('expo-file-system');
        const file = new File(uri);
        if (file.exists) file.delete();
    },
};
