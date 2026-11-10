"""Load/check an approved six-column dataset. No funnel, visualization or insights are computed."""
from pathlib import Path
from activity_dataset import COLUMNS, EVENTS, MISSIONS, SOURCES, STATUSES, utc_time

def load_activity(path):
    import pandas as pd
    frame = pd.read_csv(Path(path), encoding='utf-8-sig', dtype='string', keep_default_na=False)
    if tuple(frame.columns) != COLUMNS:
        raise ValueError('Unexpected columns: refuse unnecessary personal data')
    if not frame.participant_id.str.fullmatch(r'p_[a-f0-9]{64}').all():
        raise ValueError('Expected period-scoped HMAC pseudonyms')
    if not frame.event_type.isin(EVENTS).all() or not frame.mission_code.isin(MISSIONS).all():
        raise ValueError('Unknown event/mission')
    if ((frame.event_type == 'NFC_FIRST_UNLOCK') & (frame.mission_code == 'SHOW_ME')).any():
        raise ValueError('SHOW ME cannot be an NFC unlock event')
    if not frame.status.isin(STATUSES).all() or not (frame.source_type == frame.event_type.map(SOURCES)).all():
        raise ValueError('Unknown source/status')
    for value in frame.event_time: utc_time(value)
    frame['event_time'] = pd.to_datetime(frame.event_time, format='ISO8601', utc=True, errors='raise')
    # Do not drop identical-looking rows: distinct source records can share the same six columns.
    # Event-level deduplication must happen on staging record_key before this dataset is created.
    return frame

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='Schema validation only; does not print participant rows')
    parser.add_argument('csv_path')
    args = parser.parse_args()
    data = load_activity(args.csv_path)
    print(f'Schema valid: {len(data)} rows. No funnel calculation performed.')
