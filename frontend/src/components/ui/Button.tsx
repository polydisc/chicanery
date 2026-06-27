import classNames from 'classnames';
import React, { forwardRef, ButtonHTMLAttributes } from 'react';

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

const Button = forwardRef<HTMLButtonElement, ButtonProps>(({ className, type, ...props }, ref) => (
    <button
        ref={ref}
        type={type ?? 'button'}
        className={classNames('rounded-lg bg-primary py-3 px-20 text-white text-sm w-full', className)}
        {...props}
    />
));
Button.displayName = 'Button';

export default Button;
