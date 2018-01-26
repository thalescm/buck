from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from __future__ import with_statement

from . import struct

import copy

import unittest


class StructTest(unittest.TestCase):
    def testPropertyAccess(self):
        self.assertEqual(struct.struct(foo="bar").foo, "bar")

    def testJsonSerialization(self):
        self.assertEqual(
            struct.struct(foo="bar").to_json(),
            "{\"foo\":\"bar\"}")

    def testJsonKeysAreSorted(self):
        self.assertEqual(
            struct.struct(c="c", b="b", a="a").to_json(),
            "{\"a\":\"a\",\"b\":\"b\",\"c\":\"c\"}")

    def testNestedJsonSerialization(self):
        self.assertEqual(
            struct.struct(foo=struct.struct(bar="baz")).to_json(),
            "{\"foo\":{\"bar\":\"baz\"}}")

    def testCannotMutateAField(self):
        with self.assertRaisesRegexp(
                AttributeError,
                "Mutation of struct attributes \('foo'\) is not allowed."):
            struct.struct(foo="foo").foo = "bar"

    def testCanCopy(self):
        original = struct.struct(foo="bar")
        copied = copy.copy(original)
        self.assertEqual(original, copied)
        self.assertIsNot(original, copied)

    def testCanDeepCopy(self):
        original = struct.struct(foo="bar")
        deepcopied = copy.deepcopy(original)
        self.assertEqual(original, deepcopied)
        self.assertIsNot(original, deepcopied)

    def testInequality(self):
        x = struct.struct(foo="bar")
        y = struct.struct(foo="baz")
        self.assertNotEqual(x, y)

    def testCanUseAsAHashKey(self):
        x = struct.struct(foo="bar")
        y = struct.struct(foo="baz")
        dictionary = {
            x: "x",
            y: "y",
        }
        self.assertEqual(dictionary[x], "x")
        self.assertNotEqual(dictionary[y], "x")

    def testHasAttrForExistingAttribute(self):
        self.assertTrue(hasattr(struct.struct(foo="bar"), "foo"))

    def testHasAttrForNonExistingAttribute(self):
        self.assertFalse(hasattr(struct.struct(foo="bar"), "does_not_exist"))

    def testGetAttrForExistingAttribute(self):
        x = struct.struct(foo="bar")
        self.assertEqual("bar", getattr(x, "foo"))

    def testGetAttrForNonExistingAttribute(self):
        x = struct.struct(foo="bar")
        self.assertEqual("default", getattr(x, "does_not_exist", "default"))

    def testRepr(self):
        x = struct.struct(foo="bar")
        self.assertEqual("struct(foo='bar')", repr(x))

    def testNestedRepr(self):
        x = struct.struct(foo="bar")
        y = struct.struct(x=x)
        self.assertEqual("struct(x=struct(foo='bar'))", repr(y))
