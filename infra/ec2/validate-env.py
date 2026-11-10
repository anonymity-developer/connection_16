"""Validate deployment settings without printing any secret. Does not execute dotenv."""
import base64
import re
import sys
from pathlib import Path


def validate(text):
    values = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        if not separator or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or key in values:
            raise ValueError('Malformed or duplicate environment key')
        # Literal single quotes are the recommended Compose format for external credentials.
        if value.startswith("'") and value.endswith("'"):
            value = value[1:-1]
        elif any(c in value for c in ('$', '"', '#', ' ', '\t', "'")):
            raise ValueError(f'{key}: use a literal single-quoted dotenv value')
        values[key] = value
    for key in ('DB_PASSWORD', 'DB_ROOT_PASSWORD', 'ADMIN_BOOTSTRAP_PASSWORD', 'AUTH_HMAC_KEY'):
        value = values.get(key, '')
        if len(value) < 15 or 'change-me' in value.lower():
            raise ValueError(f'{key}: replace the placeholder with a strong production secret')
    if values['DB_PASSWORD'] == values['DB_ROOT_PASSWORD']:
        raise ValueError('Use separate database passwords')
    try:
        if len(base64.b64decode(values['AUTH_HMAC_KEY'], validate=True)) < 32:
            raise ValueError()
    except (ValueError, TypeError):
        raise ValueError('AUTH_HMAC_KEY: use at least 32 random bytes in Base64') from None
    if not re.fullmatch(r'admin:[a-zA-Z0-9_.-]+', values.get('ADMIN_BOOTSTRAP_ID', '')):
        raise ValueError('ADMIN_BOOTSTRAP_ID: use the admin: namespace')
    if not 15 <= len(values['ADMIN_BOOTSTRAP_PASSWORD']) <= 64:
        raise ValueError('ADMIN_BOOTSTRAP_PASSWORD: expected 15-64 characters')
    if values.get('AUTH_ENABLED') not in ('true', 'false'):
        raise ValueError('AUTH_ENABLED: expected true or false')
    if values.get('AUTH_ENABLED') == 'true':
        token = bool(values.get('MATTERMOST_TOKEN'))
        login_id = bool(values.get('MATTERMOST_LOGIN_ID'))
        login_password = bool(values.get('MATTERMOST_LOGIN_PASSWORD'))
        if login_id != login_password or token == (login_id and login_password):
            raise ValueError('Choose one complete Mattermost sender authentication method')
        channel_id = bool(values.get('MATTERMOST_CHANNEL_ID'))
        team = bool(values.get('MATTERMOST_TEAM_NAME'))
        channel = bool(values.get('MATTERMOST_CHANNEL_NAME'))
        if team != channel or channel_id == (team and channel):
            raise ValueError('Choose one complete Mattermost channel selection method')
        if not values.get('MATTERMOST_BASE_URL', '').startswith('https://'):
            raise ValueError('MATTERMOST_BASE_URL: HTTPS is required')
    domain = values.get('APP_DOMAIN', '')
    if domain and (len(domain) > 253 or not re.fullmatch(
        r'(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}', domain
    ) or domain.endswith(('.example.com', '.invalid', '.localhost')) or domain == 'example.com'):
        raise ValueError('APP_DOMAIN: expected a real lower-case DNS name without scheme or path')
    # Prevent a remote environment from redirecting Compose or AWS to another target.
    allowed = set(Path(__file__).resolve().parents[2].joinpath('.env.production.example').read_text().splitlines())
    allowed = {line.partition('=')[0] for line in allowed if line and not line.startswith('#')}
    allowed |= {'AUTH_OTP_TTL_SECONDS', 'AUTH_RESEND_SECONDS', 'AUTH_OTP_MAX_ATTEMPTS',
                'AUTH_OTP_ACCOUNT_LIMIT', 'AUTH_LOGIN_ACCOUNT_LIMIT', 'AUTH_IP_PER_MINUTE'}
    if values.keys() - allowed:
        raise ValueError('Unsupported production environment keys')
    return domain


if __name__ == '__main__':
    try:
        print(validate(Path(sys.argv[1]).read_text(encoding='utf-8-sig')))
    except (ValueError, OSError) as exc:
        sys.exit(str(exc))
