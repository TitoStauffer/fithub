import type { PhotoSource } from './assessmentApi';

/**
 * Câmera do aparelho pelo expo-image-picker. Os módulos nativos são carregados só no uso, para as telas que não
 * tiram foto (e os testes delas) não dependerem deles.
 */
export const expoPhotoSource: PhotoSource = {
    async take() {
        const ImagePicker = await import('expo-image-picker');
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) return null;
        const result = await ImagePicker.launchCameraAsync({
            mediaTypes: ['images'],
            quality: 0.7,
            exif: false,
        });
        const asset = result.canceled ? undefined : result.assets[0];
        if (!asset) return null;
        return { uri: asset.uri, contentType: asset.mimeType === 'image/png' ? 'image/png' : 'image/jpeg' };
    },
    async discard(uri) {
        const { File } = await import('expo-file-system');
        const file = new File(uri);
        if (file.exists) file.delete();
    },
};
