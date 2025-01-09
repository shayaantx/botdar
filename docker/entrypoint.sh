#!/bin/bash

function addConfiguration {
    environmentVariableName=$1
    environmentVariableValue=$2
    propertiesFile=$3

    echo "${environmentVariableName}=${environmentVariableValue}" >> "${propertiesFile}"
}

propertiesFile="/home/botdarr/config/properties"

if [ ! -e "$propertiesFile" ]; then
    mkdir -p "/home/botdarr/config"
    [[ ! -z "${MATRIX_USERNAME}" ]] && addConfiguration "matrix-username" "${MATRIX_USERNAME}" "${propertiesFile}"
    [[ ! -z "${MATRIX_PASSWORD}" ]] && addConfiguration "matrix-password" "${MATRIX_PASSWORD}" "${propertiesFile}"
    [[ ! -z "${MATRIX_ROOM}" ]] && addConfiguration "matrix-room" "${MATRIX_ROOM}" "${propertiesFile}"
    [[ ! -z "${MATRIX_HOME_SERVER_URL}" ]] && addConfiguration "matrix-home-server-url" "${MATRIX_HOME_SERVER_URL}" "${propertiesFile}"

    [[ ! -z "${TELEGRAM_TOKEN}" ]] && addConfiguration "telegram-token" "${TELEGRAM_TOKEN}" "${propertiesFile}"
    [[ ! -z "${TELEGRAM_PRIVATE_CHANNELS}" ]] && addConfiguration "telegram-private-channels" "${TELEGRAM_PRIVATE_CHANNELS}" "${propertiesFile}"
    [[ ! -z "${TELEGRAM_PRIVATE_GROUPS}" ]] && addConfiguration "telegram-private-groups" "${TELEGRAM_PRIVATE_GROUPS}" "${propertiesFile}"

    [[ ! -z "${SLACK_BOT_TOKEN}" ]] && addConfiguration "slack-bot-token" "${SLACK_BOT_TOKEN}" "${propertiesFile}"
    [[ ! -z "${SLACK_APP_TOKEN}" ]] && addConfiguration "slack-app-token" "${SLACK_APP_TOKEN}" "${propertiesFile}"
    [[ ! -z "${SLACK_CHANNELS}" ]] && addConfiguration "slack-channels" "${SLACK_CHANNELS}" "${propertiesFile}"

    [[ ! -z "${DISCORD_TOKEN}" ]] && addConfiguration "discord-token" "${DISCORD_TOKEN}" "${propertiesFile}"
    [[ ! -z "${DISCORD_CHANNELS}" ]] && addConfiguration "discord-channels" "${DISCORD_CHANNELS}" "${propertiesFile}"

    [[ ! -z "${RADARR_URL}" ]] && addConfiguration "radarr-url" "${RADARR_URL}" "${propertiesFile}"
    [[ ! -z "${RADARR_TOKEN}" ]] && addConfiguration "radarr-token" "${RADARR_TOKEN}" "${propertiesFile}"
    [[ ! -z "${RADARR_DEFAULT_PROFILE}" ]] && addConfiguration "radarr-default-profile" "${RADARR_DEFAULT_PROFILE}" "${propertiesFile}"
    [[ ! -z "${RADARR_PATH}" ]] && addConfiguration "radarr-path" "${RADARR_PATH}" "${propertiesFile}"
    [[ ! -z "${RADARR_URL_BASE}" ]] && addConfiguration "radarr-url-base" "${RADARR_URL_BASE}" "${propertiesFile}"

    [[ ! -z "${SONARR_URL}" ]] && addConfiguration "sonarr-url" "${SONARR_URL}" "${propertiesFile}"
    [[ ! -z "${SONARR_TOKEN}" ]] && addConfiguration "sonarr-token" "${SONARR_TOKEN}" "${propertiesFile}"
    [[ ! -z "${SONARR_DEFAULT_PROFILE}" ]] && addConfiguration "sonarr-default-profile" "${SONARR_DEFAULT_PROFILE}" "${propertiesFile}"
    [[ ! -z "${SONARR_PATH}" ]] && addConfiguration "sonarr-path" "${SONARR_PATH}" "${propertiesFile}"
    [[ ! -z "${SONARR_URL_BASE}" ]] && addConfiguration "sonarr-url-base" "${SONARR_URL_BASE}" "${propertiesFile}"
    [[ ! -z "${SONARR_V4}" ]] && addConfiguration "sonarr-v4" "${SONARR_V4}" "${propertiesFile}"

    [[ ! -z "${LIDARR_URL}" ]] && addConfiguration "lidarr-url" "${LIDARR_URL}" "${propertiesFile}"
    [[ ! -z "${LIDARR_TOKEN}" ]] && addConfiguration "lidarr-token" "${LIDARR_TOKEN}" "${propertiesFile}"
    [[ ! -z "${LIDARR_DEFAULT_QUALITY_PROFILE}" ]] && addConfiguration "lidarr-default-quality-profile" "${LIDARR_DEFAULT_QUALITY_PROFILE}" "${propertiesFile}"
    [[ ! -z "${LIDARR_DEFAULT_METADATA_PROFILE}" ]] && addConfiguration "lidarr-default-metadata-profile" "${LIDARR_DEFAULT_METADATA_PROFILE}" "${propertiesFile}"
    [[ ! -z "${LIDARR_PATH}" ]] && addConfiguration "lidarr-path" "${LIDARR_PATH}" "${propertiesFile}"
    [[ ! -z "${LIDARR_URL_BASE}" ]] && addConfiguration "lidarr-url-base" "${LIDARR_URL_BASE}" "${propertiesFile}"


    [[ ! -z "${MAX_REQUESTS_THRESHOLD}" ]] && addConfiguration "max-requests-threshold" "${MAX_REQUESTS_THRESHOLD}" "${propertiesFile}"
    [[ ! -z "${MAX_ARTIST_REQUESTS_PER_USER}" ]] && addConfiguration "max-artist-requests-per-user" "${MAX_ARTIST_REQUESTS_PER_USER}" "${propertiesFile}"
    [[ ! -z "${MAX_SHOW_REQUESTS_PER_USER}" ]] && addConfiguration "max-show-requests-per-user" "${MAX_SHOW_REQUESTS_PER_USER}" "${propertiesFile}"
    [[ ! -z "${MAX_MOVIE_REQUESTS_PER_USER}" ]] && addConfiguration "max-movie-requests-per-user" "${MAX_MOVIE_REQUESTS_PER_USER}" "${propertiesFile}"
    [[ ! -z "${EXISTING_ITEM_PATHS_BLACKLIST}" ]] && addConfiguration "existing-item-paths-blacklist" "${EXISTING_ITEM_PATHS_BLACKLIST}" "${propertiesFile}"
    [[ ! -z "${STATUS_ENDPOINTS}" ]] && addConfiguration "status-endpoints" "${STATUS_ENDPOINTS}" "${propertiesFile}"
    [[ ! -z "${LOG_LEVEL}" ]] && addConfiguration "log-level" "${LOG_LEVEL}" "${propertiesFile}"
    [[ ! -z "${NOTIFICATION_INTERVAL}" ]] && addConfiguration "notification-interval" "${NOTIFICATION_INTERVAL}" "${propertiesFile}"

    addConfiguration "max-downloads-to-show" "${MAX_DOWNLOADS_TO_SHOW:-20}" "${propertiesFile}"
    addConfiguration "max-results-to-show" "${MAX_RESULTS_TO_SHOW:-20}" "${propertiesFile}"
    addConfiguration "command-prefix" "${COMMAND_PREFIX:-!}" "${propertiesFile}"
    addConfiguration "timeout" "${TIMEOUT:-5000}" "${propertiesFile}"
fi

exec "$@"