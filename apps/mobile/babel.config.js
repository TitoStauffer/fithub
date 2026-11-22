// Babel do app mobile (DEV-0021): o preset do Expo, usado pelo Metro e pelo jest-expo.
module.exports = function (api) {
    api.cache(true);
    return { presets: ['babel-preset-expo'] };
};
