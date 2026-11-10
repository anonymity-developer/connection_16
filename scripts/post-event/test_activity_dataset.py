import importlib.util
import tempfile
import unittest
from pathlib import Path
from activity_dataset import transform, cell, write_fixture

class ActivityContract(unittest.TestCase):
    def row(self, **values):
        return dict(internal_user_id=7,event_type='LETTER_GRANTED',mission_code='SHOW_ME',event_time='2026-01-01T00:00:00.000001Z',status='COMPLETED',source_type='mission_progress.completed_at',record_key='grant:7:1') | values
    def test_pseudonym_is_stable_within_period_and_changes_across_period_or_key(self):
        a=transform([self.row()],b'a'*32,'period1')[0]
        self.assertEqual(a,transform([self.row()],b'a'*32,'period1')[0])
        self.assertNotEqual(a['participant_id'],transform([self.row()],b'a'*32,'period2')[0]['participant_id'])
        self.assertNotEqual(a['participant_id'],transform([self.row()],b'b'*32,'period1')[0]['participant_id'])
        self.assertEqual(a['event_time'],'2026-01-01T00:00:00.000001Z')
        self.assertNotIn('internal_user_id',a);self.assertNotIn('record_key',a)
    def test_event_key_dedup_keeps_distinct_sources_and_rejects_conflict(self):
        self.assertEqual(len(transform([self.row(),self.row()],b'a'*32,'p')),1)
        self.assertEqual(len(transform([self.row(),self.row(record_key='grant:7:2')],b'a'*32,'p')),2)
        with self.assertRaises(ValueError):transform([self.row(),self.row(status='CHANGED')],b'a'*32,'p')
    def test_rejects_extra_pii_unknown_events_non_utc_and_show_nfc(self):
        for row in [self.row(real_name='forbidden'),self.row(event_type='PAGE_DWELL'),self.row(event_time='2026-01-01T00:00:00'),self.row(event_type='NFC_FIRST_UNLOCK'),self.row(source_type='raw message'),self.row(status='raw message'),self.row(internal_user_id=0)]:
            with self.assertRaises(ValueError):transform([row],b'a'*32,'p')
    def test_mixed_fractional_precision_is_sorted_by_actual_utc_time(self):
        rows=transform([self.row(),self.row(event_time='2026-01-01T00:00:00Z',record_key='grant:7:2')],b'a'*32,'p')
        self.assertEqual(rows[0]['event_time'],'2026-01-01T00:00:00Z')
        self.assertEqual(rows[1]['event_time'],'2026-01-01T00:00:00.000001Z')
    def test_csv_formula_cells_are_neutralized(self):
        for s in ['=HYPERLINK("x")',' \t+CMD\nnext','-1+1','@SUM(1)','\r=1','\n=1']:
            self.assertTrue(cell(s).startswith("'"))
        self.assertEqual(cell('normal'),'normal')
    @unittest.skipUnless(importlib.util.find_spec('pandas'),'pandas is not installed')
    def test_synthetic_fixture_loads_without_funnel_or_personal_output(self):
        from pandas_prepare import load_activity
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'fixture.csv';self.assertEqual(write_fixture(path),5)
            frame=load_activity(path);self.assertEqual(len(frame),5);self.assertEqual(frame.participant_id.nunique(),2)
            self.assertTrue(str(frame.event_time.dtype).startswith('datetime64['))
if __name__=='__main__':unittest.main()
