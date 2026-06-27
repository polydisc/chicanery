import classNames from 'classnames';
import { forwardRef, InputHTMLAttributes } from 'react';

export type InputTextProps = InputHTMLAttributes<HTMLInputElement>;

const InputText = forwardRef<HTMLInputElement, InputTextProps>(({ className, ...props }, ref) => (
    <input
        ref={ref}
        inputMode='text'
        type='text'
        className={classNames('p-3 rounded-lg w-full text-black', className)}
        {...props}
    />
));
InputText.displayName = 'InputText';

export default InputText;
