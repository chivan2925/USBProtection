import unittest

from check_design import check_css


FOCUS = ".button:focused { -fx-border-color: #138568; }"


class DesignCheckTest(unittest.TestCase):
    def test_offline_local_styles_and_shared_focus(self):
        css = '@import "theme.css"; .button { -fx-graphic: url(icon.png); }' + FOCUS
        self.assertEqual([], check_css(css))

    def test_rejects_quoted_and_url_remote_styles(self):
        for directive in ('@import "https://fixture.invalid/theme.css";',
                          "@import url('https://fixture.invalid/theme.css');",
                          '.primary { -fx-graphic: url(//fixture.invalid/icon.png); }'):
            with self.subTest(directive=directive):
                self.assertIn("JavaFX stylesheet loads a remote asset", check_css(directive + FOCUS))

    def test_requires_substantive_general_focus_rule(self):
        for candidate in (f"/*{FOCUS}*/", FOCUS.replace(".button", ".button.nav"), ".button:focused {}"):
            with self.subTest(candidate=candidate):
                self.assertTrue(any("shared .button:focused" in error for error in check_css(candidate)))


if __name__ == "__main__":
    unittest.main()
