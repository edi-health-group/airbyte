#
# Copyright (c) 2021 Airbyte, Inc., all rights reserved.
#

from typing import Mapping

from airbyte_cdk.sources.lcc.interpolation.interpolation import Interpolation
from airbyte_cdk.sources.lcc.interpolation.jinja import JinjaInterpolation


class InterpolatedMapping:
    def __init__(self, mapping: Mapping[str, str], interpolation: Interpolation = JinjaInterpolation()):
        self._mapping = mapping
        self._interpolation = interpolation

    def eval(self, config, **kwargs):
        interpolated_values = {
            self._interpolation.eval(name, config, **kwargs): self._eval(value, config, **kwargs) for name, value in self._mapping.items()
        }
        return interpolated_values

    def _eval(self, value, config, **kwargs):
        if type(value) == str:
            return self._interpolation.eval(value, config, **kwargs)
        else:
            return value
