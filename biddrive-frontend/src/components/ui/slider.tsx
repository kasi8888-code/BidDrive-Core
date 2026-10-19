import React from "react";
import { cn } from "@/lib/utils";

export interface SliderProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "defaultValue"> {
  value?: number[];
  defaultValue?: number[];
  onValueChange?: (value: number[]) => void;
  min?: number;
  max?: number;
  step?: number;
}

export const Slider = React.forwardRef<HTMLInputElement, SliderProps>(
  ({ className, value, defaultValue = [0], onValueChange, min = 0, max = 1, step = 0.01, ...props }, ref) => {
    const currentValue = value ? value[0] : defaultValue[0];

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const num = parseFloat(e.target.value);
      onValueChange?.([num]);
    };

    return (
      <div className={cn("relative flex w-full touch-none select-none items-center", className)}>
        <input
          ref={ref}
          type="range"
          min={min}
          max={max}
          step={step}
          value={currentValue}
          onChange={handleChange}
          className="w-full accent-amber-500 cursor-pointer h-2 bg-gray-200 rounded-lg appearance-none dark:bg-gray-700"
          {...props}
        />
      </div>
    );
  }
);
Slider.displayName = "Slider";
