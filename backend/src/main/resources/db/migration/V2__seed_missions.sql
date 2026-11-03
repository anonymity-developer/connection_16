INSERT INTO missions (id, code, letter, title, hint, sort_order) VALUES
    (1, 'SHOW_ME', 'S', 'SHOW ME', '나를 소개하는 프로필을 완성해 보세요.', 1),
    (2, 'SYNC_ME', 'S', 'SYNC ME', '나와 잘 맞는 동료를 발견해 보세요.', 2),
    (3, 'MAKE_A_WISH', 'A', 'MAKE A WISH', '소원 하나를 호수에 남겨 보세요.', 3),
    (4, 'FAST_TAP', 'F', 'FAST TAP', '10초 동안 빠르게 탭해 보세요.', 4),
    (5, 'SAY_SOMETHING', 'Y', 'SAY SOMETHING', '동료에게 응원 한마디를 남겨 보세요.', 5);

INSERT INTO event_completion_sequence (id, next_value) VALUES (1, 1);
